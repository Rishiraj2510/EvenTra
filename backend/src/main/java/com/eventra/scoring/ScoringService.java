package com.eventra.scoring;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.model.FitBreakdown;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic scoring engine for evaluating volunteer-to-task compatibility.
 * <p>
 * Evaluates hard constraints (mandatory skills, availability, and working hours)
 * and calculates a multi-factor fit score based on skill proficiency, past experience,
 * performance rating, workload, and interest alignment.
 * </p>
 */
public final class ScoringService {

    private static final LocalTime MORNING_START = LocalTime.of(5, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(12, 0);
    private static final LocalTime EVENING_START = LocalTime.of(17, 0);

    private static final Pattern TITLE_STRIP_PATTERN = Pattern.compile("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$");

    private ScoringService() {
    }

    /**
     * Converts a skill level string to a numeric value in (0.0, 1.0].
     * Trims the level and finds the index in ModelValues.SKILL_LEVELS ignoring case.
     * Returns (index + 1) / (double) ModelValues.SKILL_LEVELS.size().
     * Returns 0.0 for null or unknown level.
     * (Beginner=1/3, Intermediate=2/3, Advanced=1.0.)
     *
     * @param level skill level name
     * @return numeric level value [0.0, 1.0]
     */
    private static double levelValue(String level) {
        if (level == null) {
            return 0.0;
        }
        String trimmed = level.trim();
        for (int i = 0; i < ModelValues.SKILL_LEVELS.size(); i++) {
            if (ModelValues.SKILL_LEVELS.get(i).equalsIgnoreCase(trimmed)) {
                return (i + 1) / (double) ModelValues.SKILL_LEVELS.size();
            }
        }
        return 0.0;
    }

    /**
     * Determines the availability slot (Morning, Afternoon, or Evening) for a given start time.
     * Boundaries: 05:00-11:59 (Morning), 12:00-16:59 (Afternoon), 17:00-04:59 (Evening).
     *
     * @param time task start time
     * @return slot name from ModelValues.SLOTS
     */
    public static String determineSlot(LocalTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time cannot be null");
        }
        if (time.isBefore(MORNING_START)) {
            // 00:00 - 04:59 counts as Evening
            return ModelValues.SLOTS.get(2);
        }
        if (time.isBefore(AFTERNOON_START)) {
            // 05:00 - 11:59 counts as Morning
            return ModelValues.SLOTS.get(0);
        }
        if (time.isBefore(EVENING_START)) {
            // 12:00 - 16:59 counts as Afternoon
            return ModelValues.SLOTS.get(1);
        }
        // 17:00 - 23:59 counts as Evening
        return ModelValues.SLOTS.get(2);
    }

    /**
     * Calculates the deterministic fit score of a volunteer for a task.
     * Pure function: produces identical output for identical input, has no static mutable state,
     * performs no I/O, and does not mutate any input arguments.
     * <p>
     * Hard constraints evaluated in strict priority order:
     * 1. Missing mandatory skill: volunteer must possess all mandatory skills.
     * 2. Not available: volunteer availability must cover task date day of week (ModelValues.DAYS)
     *    and start-time slot (ModelValues.SLOTS).
     * 3. Over hours: duration is calculated using Duration d = Duration.between(start, end).
     *    If d.isNegative(), Duration.ofHours(24) is added (overnight task).
     *    start == end is a 0-minute task (feasible).
     *    Infeasible if d.compareTo(Duration.ofHours(maxHours)) &gt; 0, with reason text:
     *    "Over hours: task duration &lt;minutes&gt; min exceeds max &lt;maxHours&gt; h".
     * </p>
     * <p>
     * Skill factor calculation builds the list of task skills from mandatory + preferred,
     * deduplicated case-insensitively after trim (keeping the first occurrence and preserving order),
     * and averages the volunteer's highest skill level value for each required task skill.
     * </p>
     *
     * @param volunteer volunteer candidate, non-null
     * @param task task candidate, non-null
     * @param config scoring weights and bonus configuration, non-null
     * @return evaluation result containing feasibility, fit score, factor breakdown, and flags
     */
    public static FitResult score(SolverVolunteer volunteer, SolverTask task, ScoringConfig config) {
        if (volunteer == null) {
            throw new IllegalArgumentException("volunteer cannot be null");
        }
        if (task == null) {
            throw new IllegalArgumentException("task cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        // ---------------------------------------------------------------------
        // STEP 2: HARD CONSTRAINTS (in strict priority order)
        // ---------------------------------------------------------------------

        // Constraint 1: Missing mandatory skill
        for (String mandatorySkill : task.mandatorySkills()) {
            String trimmedMandatory = mandatorySkill.trim();
            boolean hasSkill = false;
            for (SkillLevel sl : volunteer.skills()) {
                if (sl != null && sl.getName() != null && sl.getName().trim().equalsIgnoreCase(trimmedMandatory)) {
                    hasSkill = true;
                    break;
                }
            }
            if (!hasSkill) {
                List<String> flags = task.critical() ? List.of("Critical task") : List.of();
                return new FitResult(false, "Missing mandatory skill: " + mandatorySkill, 0.0, null, flags);
            }
        }

        // Constraint 2: Not available
        String requiredDay = ModelValues.DAYS.get(task.date().getDayOfWeek().getValue() - 1);
        String requiredSlot = determineSlot(task.startTime());
        boolean available = false;
        for (AvailabilitySlot slot : volunteer.availability()) {
            if (slot != null && slot.getDay() != null && slot.getSlot() != null) {
                String slotDay = slot.getDay().trim();
                String slotName = slot.getSlot().trim();
                boolean dayMatches = slotDay.equalsIgnoreCase(requiredDay);
                boolean slotMatches = slotName.equalsIgnoreCase(requiredSlot);
                if (dayMatches && slotMatches) {
                    available = true;
                    break;
                }
            }
        }
        if (!available) {
            List<String> flags = task.critical() ? List.of("Critical task") : List.of();
            return new FitResult(false, "Not available for " + requiredDay + " (" + requiredSlot + ")", 0.0, null, flags);
        }

        // Constraint 3: Over hours
        Duration d = Duration.between(task.startTime(), task.endTime());
        if (d.isNegative()) {
            d = d.plus(Duration.ofHours(24));
        }
        int maxHours = volunteer.maxHours() > 0 ? volunteer.maxHours() : config.defaultMaxHours();
        if (d.compareTo(Duration.ofHours(maxHours)) > 0) {
            List<String> flags = task.critical() ? List.of("Critical task") : List.of();
            return new FitResult(false, "Over hours: task duration " + d.toMinutes() + " min exceeds max " + maxHours + " h", 0.0, null, flags);
        }

        // ---------------------------------------------------------------------
        // STEP 3: FIT COMPUTATION
        // ---------------------------------------------------------------------

        // 1. Skill factor: average of level values over distinct task skills (mandatory + preferred)
        List<String> distinctTaskSkills = new ArrayList<>();
        Set<String> seenSkills = new HashSet<>();
        for (String s : task.mandatorySkills()) {
            if (s != null) {
                String trimmed = s.trim();
                if (seenSkills.add(trimmed.toLowerCase(Locale.ROOT))) {
                    distinctTaskSkills.add(trimmed);
                }
            }
        }
        for (String s : task.preferredSkills()) {
            if (s != null) {
                String trimmed = s.trim();
                if (seenSkills.add(trimmed.toLowerCase(Locale.ROOT))) {
                    distinctTaskSkills.add(trimmed);
                }
            }
        }

        double skillFactor;
        if (distinctTaskSkills.isEmpty()) {
            skillFactor = 1.0;
        } else {
            double totalSkillScore = 0.0;
            for (String taskSkill : distinctTaskSkills) {
                double volunteerSkillLevelVal = 0.0;
                for (SkillLevel vSkill : volunteer.skills()) {
                    if (vSkill != null && vSkill.getName() != null && vSkill.getName().trim().equalsIgnoreCase(taskSkill)) {
                        double val = levelValue(vSkill.getLevel());
                        if (val > volunteerSkillLevelVal) {
                            volunteerSkillLevelVal = val;
                        }
                    }
                }
                totalSkillScore += volunteerSkillLevelVal;
            }
            skillFactor = totalSkillScore / distinctTaskSkills.size();
        }

        // 2. Experience factor: min(pastEventsCount / 5.0, 1.0)
        double experienceFactor = Math.min(volunteer.pastEventsCount() / 5.0, 1.0);

        // 3. Performance factor: averageRating / 5.0, or 0.5 if null
        double performanceFactor = volunteer.averageRating() == null
                ? 0.5
                : (volunteer.averageRating() / 5.0);

        // 4. Load factor: 1.0 (single-task model; load will be used when multi-task is added)
        double loadFactor = 1.0;

        // 5. Interest factor: 1.0 if any interest matches task skill or any word of task title
        double interestFactor = 0.0;
        if (!volunteer.interests().isEmpty()) {
            Set<String> matchTargets = new HashSet<>();
            for (String s : task.mandatorySkills()) {
                if (s != null && !s.isBlank()) {
                    matchTargets.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            for (String s : task.preferredSkills()) {
                if (s != null && !s.isBlank()) {
                    matchTargets.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            String[] titleTokens = task.title().split("\\s+");
            for (String token : titleTokens) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    matchTargets.add(trimmed.toLowerCase(Locale.ROOT));
                    String stripped = TITLE_STRIP_PATTERN.matcher(trimmed).replaceAll("");
                    if (!stripped.isEmpty()) {
                        matchTargets.add(stripped.toLowerCase(Locale.ROOT));
                    }
                }
            }
            for (String interest : volunteer.interests()) {
                if (interest != null && matchTargets.contains(interest.trim().toLowerCase(Locale.ROOT))) {
                    interestFactor = 1.0;
                    break;
                }
            }
        }

        // Weights preparation (rescaling if critical)
        ScoreWeights baseWeights = config.weights();
        double wSkill = baseWeights.skill();
        double wExp = baseWeights.experience();
        double wPerf = baseWeights.performance();
        double wLoad = baseWeights.load();
        double wInterest = baseWeights.interest();

        if (task.critical()) {
            wExp *= 1.5;
            wPerf *= 1.5;
            double weightSum = wSkill + wExp + wPerf + wLoad + wInterest;
            wSkill = (wSkill / weightSum) * 100.0;
            wExp = (wExp / weightSum) * 100.0;
            wPerf = (wPerf / weightSum) * 100.0;
            wLoad = (wLoad / weightSum) * 100.0;
            wInterest = (wInterest / weightSum) * 100.0;
        }

        // Weighted fit calculation: 100 * (wS*skill + wE*exp + wP*perf + wL*load + wI*interest) / 100
        double fit = (wSkill * skillFactor)
                + (wExp * experienceFactor)
                + (wPerf * performanceFactor)
                + (wLoad * loadFactor)
                + (wInterest * interestFactor);

        // Flags & first-timer bonus
        List<String> flags = new ArrayList<>();
        if (task.critical()) {
            flags.add("Critical task");
        }
        if (volunteer.pastEventsCount() == 0 && !task.critical()) {
            fit += config.firstTimerBonus();
            flags.add("First-timer");
        }

        // Clamping to [0.0, 100.0]
        if (fit > 100.0) {
            fit = 100.0;
        } else if (fit < 0.0) {
            fit = 0.0;
        }

        FitBreakdown breakdown = new FitBreakdown(skillFactor, experienceFactor, performanceFactor, loadFactor, interestFactor);
        return new FitResult(true, null, fit, breakdown, flags);
    }
}
