package com.eventra.plan;

import com.eventra.event.EventConflictException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.plan.dto.AssignmentDto;
import com.eventra.plan.dto.BreakdownDto;
import com.eventra.plan.dto.PlanResponse;
import com.eventra.plan.dto.PlanSummaryDto;
import com.eventra.scoring.model.FitBreakdown;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import com.eventra.settings.Settings;
import com.eventra.settings.SettingsNotFoundException;
import com.eventra.settings.SettingsRepository;
import com.eventra.solver.AssignmentSolver;
import com.eventra.solver.model.AssignedPair;
import com.eventra.solver.model.SolverResult;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class PlanService {

    private final EventRepository eventRepository;
    private final PlanRepository planRepository;
    private final AssignmentRepository assignmentRepository;
    private final TaskRepository taskRepository;
    private final VolunteerRepository volunteerRepository;
    private final SettingsRepository settingsRepository;

    public PlanService(
            EventRepository eventRepository,
            PlanRepository planRepository,
            AssignmentRepository assignmentRepository,
            TaskRepository taskRepository,
            VolunteerRepository volunteerRepository,
            SettingsRepository settingsRepository
    ) {
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
        this.volunteerRepository = volunteerRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public void generate(UUID eventId) {
        // 1. Lock the event
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));

        // 2. Load plan by eventId; if exists and not draft -> 409
        Optional<Plan> planOpt = planRepository.findByEventId(eventId);
        if (planOpt.isPresent() && !PlanStatus.DRAFT.equals(planOpt.get().getStatus())) {
            throw new EventConflictException("Plan can only be regenerated while it is a draft");
        }

        // 3. Load tasks and volunteers of the event
        List<Task> tasks = taskRepository.findByEventId(eventId);
        List<Volunteer> volunteers = volunteerRepository.findByEventId(eventId);
        if (tasks.isEmpty() || volunteers.isEmpty()) {
            throw new EventConflictException("Nothing to assign");
        }

        // 4. Any task with null date, start or end time -> 409, and check max size limit
        long totalSlots = 0L;
        for (Task t : tasks) {
            if (t.getTaskDate() == null || t.getStartTime() == null || t.getEndTime() == null) {
                throw new EventConflictException("Every task needs a date, start time and end time");
            }
            totalSlots += t.getHeadcount();
        }

        if (volunteers.size() > AssignmentSolver.MAX_SIZE || totalSlots > AssignmentSolver.MAX_SIZE) {
            throw new EventConflictException("Plan too large to generate");
        }

        // 5. Map to SolverTask / SolverVolunteer
        List<SolverTask> solverTasks = new ArrayList<>(tasks.size());
        Map<UUID, Task> taskMap = new HashMap<>();
        for (Task t : tasks) {
            taskMap.put(t.getId(), t);
            List<String> mand = t.getMandatorySkills() != null ? t.getMandatorySkills() : List.of();
            List<String> pref = t.getPreferredSkills() != null ? t.getPreferredSkills() : List.of();
            solverTasks.add(new SolverTask(
                    t.getId(),
                    t.getTitle(),
                    t.getTaskDate(),
                    t.getStartTime(),
                    t.getEndTime(),
                    t.getHeadcount(),
                    mand,
                    pref,
                    t.isCritical()
            ));
        }

        List<SolverVolunteer> solverVolunteers = new ArrayList<>(volunteers.size());
        Map<UUID, Volunteer> volunteerMap = new HashMap<>();
        for (Volunteer v : volunteers) {
            volunteerMap.put(v.getId(), v);
            List<SkillLevel> skills = v.getSkills() != null ? v.getSkills() : List.of();
            List<AvailabilitySlot> avail = v.getAvailability() != null ? v.getAvailability() : List.of();
            List<String> interests = v.getInterests() != null ? v.getInterests() : List.of();
            int maxH = v.getMaxHoursPerEvent() != null ? v.getMaxHoursPerEvent() : 0;
            solverVolunteers.add(new SolverVolunteer(
                    v.getId(),
                    v.getFullName(),
                    skills,
                    v.getPastEventsCount(),
                    null,
                    avail,
                    maxH,
                    interests
            ));
        }

        // Build ScoringConfig from settings
        Settings settings = settingsRepository.findById(true)
                .orElseThrow(() -> new SettingsNotFoundException("Settings configuration row not found"));
        ScoreWeights weights = new ScoreWeights(
                settings.getWeightSkill(),
                settings.getWeightExperience(),
                settings.getWeightPerformance(),
                settings.getWeightLoad(),
                settings.getWeightInterest()
        );
        ScoringConfig config = new ScoringConfig(
                weights,
                settings.getFirstTimerBonus(),
                settings.getDefaultMaxHours()
        );

        // 6. Call AssignmentSolver.solve
        SolverResult solverResult = AssignmentSolver.solve(solverVolunteers, solverTasks, config);

        // 7. Manage Plan row & flush deletions
        Plan plan;
        Instant now = Instant.now();
        if (planOpt.isPresent()) {
            plan = planOpt.get();
            assignmentRepository.deleteByPlanId(plan.getId());
            assignmentRepository.flush();

            plan.setStatus(PlanStatus.DRAFT);
            plan.setStale(false);
            plan.setGeneratedAt(now);
            plan.setApprovedAt(null);
            plan.setPublishedAt(null);
            plan.touch();
            plan = planRepository.save(plan);
        } else {
            plan = new Plan(eventId, PlanStatus.DRAFT);
            plan.setGeneratedAt(now);
            plan.setApprovedAt(null);
            plan.setPublishedAt(null);
            plan.setStale(false);
            plan.touch();
            plan = planRepository.save(plan);
        }

        // 8. Insert one Assignment per AssignedPair
        List<Assignment> newAssignments = new ArrayList<>();
        for (AssignedPair pair : solverResult.assignments()) {
            Task t = taskMap.get(pair.taskId());
            Volunteer v = volunteerMap.get(pair.volunteerId());
            FitResult fitResult = pair.fit();
            FitBreakdown bd = fitResult.breakdown();

            Assignment a = new Assignment();
            a.setPlan(plan);
            a.setTask(t);
            a.setVolunteer(v);
            a.setSlotNumber(pair.slotNumber());
            a.setFit(BigDecimal.valueOf(fitResult.fit()).setScale(3, RoundingMode.HALF_UP));

            if (bd != null) {
                a.setSkillScore(BigDecimal.valueOf(bd.skill() * 100.0).setScale(3, RoundingMode.HALF_UP));
                a.setExperienceScore(BigDecimal.valueOf(bd.experience() * 100.0).setScale(3, RoundingMode.HALF_UP));
                a.setPerformanceScore(BigDecimal.valueOf(bd.performance() * 100.0).setScale(3, RoundingMode.HALF_UP));
                a.setLoadScore(BigDecimal.valueOf(bd.load() * 100.0).setScale(3, RoundingMode.HALF_UP));
                a.setInterestScore(BigDecimal.valueOf(bd.interest() * 100.0).setScale(3, RoundingMode.HALF_UP));
            } else {
                a.setSkillScore(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                a.setExperienceScore(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                a.setPerformanceScore(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                a.setLoadScore(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                a.setInterestScore(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            }

            a.setFlags(fitResult.flags() != null ? fitResult.flags() : List.of());
            a.setReason(buildReasonText(bd));
            a.setLocked(false);

            newAssignments.add(a);
        }

        assignmentRepository.saveAll(newAssignments);
    }

    private String buildReasonText(FitBreakdown bd) {
        if (bd == null) {
            return "Feasible assignment.";
        }
        long skill = Math.round(bd.skill() * 100.0);
        long exp = Math.round(bd.experience() * 100.0);
        long perf = Math.round(bd.performance() * 100.0);
        long load = Math.round(bd.load() * 100.0);
        long interest = Math.round(bd.interest() * 100.0);
        return String.format(Locale.ROOT,
                "Skill match %d, experience %d, performance %d, workload %d, interest %d.",
                skill, exp, perf, load, interest
        );
    }

    @Transactional
    public void approve(UUID eventId) {
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));

        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventConflictException("Plan must be a draft to approve"));

        if (!PlanStatus.DRAFT.equals(plan.getStatus())) {
            throw new EventConflictException("Plan must be a draft to approve");
        }

        plan.setStatus(PlanStatus.APPROVED);
        plan.setApprovedAt(Instant.now());
        plan.touch();
        planRepository.save(plan);
    }

    @Transactional
    public void publish(UUID eventId) {
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));

        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventConflictException("Plan must be approved to publish"));

        if (!PlanStatus.APPROVED.equals(plan.getStatus())) {
            throw new EventConflictException("Plan must be approved to publish");
        }

        plan.setStatus(PlanStatus.PUBLISHED);
        plan.setPublishedAt(Instant.now());
        plan.touch();
        planRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found");
        }

        long headcountRequired = 0L;
        List<Task> eventTasks = taskRepository.findByEventId(eventId);
        for (Task t : eventTasks) {
            headcountRequired += t.getHeadcount();
        }

        Optional<Plan> planOpt = planRepository.findByEventId(eventId);
        if (planOpt.isEmpty()) {
            PlanSummaryDto emptySummary = new PlanSummaryDto(
                    0.0,
                    0L,
                    0,
                    0L,
                    headcountRequired,
                    0L
            );
            // NOTE: Run 8b computes skill gaps on read. missingSkill null means an availability/hours problem, not a skill gap; skillGapCount counts only non-null missingSkill.
            return new PlanResponse(
                    eventId,
                    PlanStatus.NONE,
                    false,
                    emptySummary,
                    List.of(),
                    List.of()
            );
        }

        Plan plan = planOpt.get();
        List<Assignment> rawAssignments = assignmentRepository.findByPlanIdWithDetailsOrderByTaskDetails(plan.getId());

        List<AssignmentDto> assignmentDtos = new ArrayList<>(rawAssignments.size());
        Map<UUID, Integer> volunteerAssignmentCounts = new HashMap<>();
        long unqualifiedAssignments = 0L;
        BigDecimal sumFit = BigDecimal.ZERO;

        for (Assignment a : rawAssignments) {
            String taskTitle = a.getTask().getTitle();
            UUID taskId = a.getTask().getId();
            String volunteerName = a.getVolunteer().getFullName();
            UUID volunteerId = a.getVolunteer().getId();

            volunteerAssignmentCounts.put(volunteerId, volunteerAssignmentCounts.getOrDefault(volunteerId, 0) + 1);

            if (a.getFlags().contains("unqualified")) {
                unqualifiedAssignments++;
            }

            sumFit = sumFit.add(a.getFit());

            double roundedFit = a.getFit().setScale(1, RoundingMode.HALF_UP).doubleValue();

            BreakdownDto breakdown = BreakdownDto.fromScores(
                    a.getSkillScore(),
                    a.getExperienceScore(),
                    a.getPerformanceScore(),
                    a.getLoadScore(),
                    a.getInterestScore()
            );

            assignmentDtos.add(new AssignmentDto(
                    a.getId(),
                    taskId,
                    taskTitle,
                    volunteerId,
                    volunteerName,
                    roundedFit,
                    breakdown,
                    a.getReason(),
                    a.getFlags(),
                    a.isLocked()
            ));
        }

        long headcountFilled = assignmentDtos.size();

        double averageFit = 0.0;
        if (headcountFilled > 0) {
            BigDecimal meanFit = sumFit.divide(BigDecimal.valueOf(headcountFilled), 1, RoundingMode.HALF_UP);
            averageFit = meanFit.doubleValue();
        }

        int workloadSpread = 0;
        if (!volunteerAssignmentCounts.isEmpty()) {
            int maxAssignments = Collections.max(volunteerAssignmentCounts.values());
            int minAssignments = Collections.min(volunteerAssignmentCounts.values());
            workloadSpread = maxAssignments - minAssignments;
        }

        // NOTE: Run 8b computes skill gaps on read. missingSkill null means an availability/hours problem, not a skill gap; skillGapCount counts only non-null missingSkill.
        long skillGapCount = 0L;

        PlanSummaryDto summary = new PlanSummaryDto(
                averageFit,
                unqualifiedAssignments,
                workloadSpread,
                headcountFilled,
                headcountRequired,
                skillGapCount
        );

        return new PlanResponse(
                eventId,
                plan.getStatus(),
                plan.isStale(),
                summary,
                assignmentDtos,
                List.of()
        );
    }
}
