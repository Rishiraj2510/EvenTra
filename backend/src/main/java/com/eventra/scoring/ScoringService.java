package com.eventra.scoring;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.model.FitBreakdown;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic scoring engine for evaluating volunteer-to-task compatibility.
 * <p>
 * Evaluates hard constraints (mandatory skills, availability, and working hours)
 * and calculates a multi-factor fit score based on skill proficiency, past experience,
 * performance rating, workload, and interest alignment.
 * </p>
 */
public class ScoringService {

    // Slot boundary constants
    public static final LocalTime MORNING_START = LocalTime.of(5, 0);
    public static final LocalTime MORNING_END = LocalTime.of(11, 59);
    public static final LocalTime AFTERNOON_START = LocalTime.of(12, 0);
    public static final LocalTime AFTERNOON_END = LocalTime.of(16, 59);
    public static final LocalTime EVENING_START = LocalTime.of(17, 0);
    public static final LocalTime EVENING_END = LocalTime.of(4, 59);

    public static final String SLOT_MORNING = "Morning";
    public static final String SLOT_AFTERNOON = "Afternoon";
    public static final String SLOT_EVENING = "Evening";

    public static final ZoneId ZONE_KOLKATA = ZoneId.of("Asia/Kolkata");

    /**
     * Skill level to numeric value mapping [0.0, 1.0], strictly increasing with level.
     */
    public static final Map<String, Double> SKILL_LEVEL_MAP = Map.of(
            "beginner", 0.25,
            "intermediate", 0.50,
            "advanced", 0.75,
            "expert", 1.0
    );

    /**
     * Determines the availability slot (Morning, Afternoon, or Evening) for a given start time.
     *
     * @param time task start time
     * @return slot name ("Morning", "Afternoon", or "Evening")
     */
    public static String determineSlot(LocalTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time cannot be null");
        }
        if (time.isBefore(MORNING_START)) {
            // 00:00 - 04:59 counts as Evening
            return SLOT_EVENING;
        }
        if (time.isBefore(AFTERNOON_START)) {
            // 05:00 - 11:59 counts as Morning
            return SLOT_MORNING;
        }
        if (time.isBefore(EVENING_START)) {
            // 12:00 - 16:59 counts as Afternoon
            return SLOT_AFTERNOON;
        }
        // 17:00 - 23:59 counts as Evening
        return SLOT_EVENING;
    }

    /**
     * Calculates the deterministic fit score of a volunteer for a task.
     * Pure function: produces identical output for identical input, has no static mutable state,
     * performs no I/O, and does not mutate any input arguments.
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
        DayOfWeek dayOfWeek = task.date().getDayOfWeek();
        String requiredSlot = determineSlot(task.startTime());
        boolean available = false;
        for (AvailabilitySlot slot : volunteer.availability()) {
            if (slot != null && slot.getDay() != null && slot.getSlot() != null) {
                String slotDay = slot.getDay().trim();
                String slotName = slot.getSlot().trim();
                boolean dayMatches = slotDay.equalsIgnoreCase(dayOfWeek.name())
                        || slotDay.equalsIgnoreCase(dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
                boolean slotMatches = slotName.equalsIgnoreCase(requiredSlot);
                if (dayMatches && slotMatches) {
                    available = true;
                    break;
                }
            }
        }
        if (!available) {
            List<String> flags = task.critical() ? List.of("Critical task") : List.of();
            return new FitResult(false, "Not available for " + dayOfWeek.name() + " (" + requiredSlot + ")", 0.0, null, flags);
        }

        // Constraint 3: Over hours
        long minutes;
        if (task.endTime().isBefore(task.startTime())) {
            minutes = Duration.between(task.startTime(), LocalTime.MAX).toMinutes() + 1
                    + Duration.between(LocalTime.MIN, task.endTime()).toMinutes();
        } else {
            minutes = Duration.between(task.startTime(), task.endTime()).toMinutes();
        }
        double durationHours = minutes / 60.0;
        int maxHours = volunteer.maxHours() > 0 ? volunteer.maxHours() : config.defaultMaxHours();
        if (durationHours > maxHours) {
            List<String> flags = task.critical() ? List.of("Critical task") : List.of();
            return new FitResult(false, "Over hours: task duration " + durationHours + "h exceeds max hours " + maxHours + "h", 0.0, null, flags);
        }

        // ---------------------------------------------------------------------
        // STEP 3: FIT COMPUTATION
        // ---------------------------------------------------------------------

        // 1. Skill factor: average of level values over all mandatory AND preferred skills
        List<String> allTaskSkills = new ArrayList<>(task.mandatorySkills());
        allTaskSkills.addAll(task.preferredSkills());

        double skillFactor;
        if (allTaskSkills.isEmpty()) {
            skillFactor = 1.0;
        } else {
            double totalSkillScore = 0.0;
            for (String taskSkill : allTaskSkills) {
                String trimmedTaskSkill = taskSkill.trim();
                double volunteerSkillLevelVal = 0.0;
                for (SkillLevel vSkill : volunteer.skills()) {
                    if (vSkill != null && vSkill.getName() != null && vSkill.getName().trim().equalsIgnoreCase(trimmedTaskSkill)) {
                        String level = vSkill.getLevel();
                        double val = 0.0;
                        if (level != null) {
                            val = SKILL_LEVEL_MAP.getOrDefault(level.trim().toLowerCase(Locale.ROOT), 0.0);
                        }
                        if (val > volunteerSkillLevelVal) {
                            volunteerSkillLevelVal = val;
                        }
                    }
                }
                totalSkillScore += volunteerSkillLevelVal;
            }
            skillFactor = totalSkillScore / allTaskSkills.size();
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
                    String stripped = trimmed.replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "");
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
