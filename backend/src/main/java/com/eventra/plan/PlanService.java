package com.eventra.plan;

import com.eventra.event.EventConflictException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.plan.dto.AssignmentDto;
import com.eventra.plan.dto.BreakdownDto;
import com.eventra.plan.dto.PlanResponse;
import com.eventra.plan.dto.PlanSummaryDto;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
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

    public PlanService(
            EventRepository eventRepository,
            PlanRepository planRepository,
            AssignmentRepository assignmentRepository,
            TaskRepository taskRepository
    ) {
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public void approve(UUID eventId) {
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));

        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventConflictException("Plan must be a draft to approve"));

        if (!PlanStatus.DRAFT.equalsIgnoreCase(plan.getStatus())) {
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

        if (!PlanStatus.APPROVED.equalsIgnoreCase(plan.getStatus())) {
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
