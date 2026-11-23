package com.eventra.feedback;

import com.eventra.common.TextRules;
import com.eventra.event.EventConflictException;
import com.eventra.event.EventNotFoundException;
import com.eventra.event.EventRepository;
import com.eventra.feedback.dto.FeedbackItemResponse;
import com.eventra.feedback.dto.UpsertFeedbackRequest;
import com.eventra.plan.Assignment;
import com.eventra.plan.AssignmentRepository;
import com.eventra.plan.Plan;
import com.eventra.plan.PlanRepository;
import com.eventra.plan.PlanStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class FeedbackService {

    private final EventRepository eventRepository;
    private final PlanRepository planRepository;
    private final AssignmentRepository assignmentRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(EventRepository eventRepository,
            PlanRepository planRepository,
            AssignmentRepository assignmentRepository,
            FeedbackRepository feedbackRepository) {
        this.eventRepository = eventRepository;
        this.planRepository = planRepository;
        this.assignmentRepository = assignmentRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public List<FeedbackItemResponse> getFeedback(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventConflictException("Plan is not published"));

        if (!PlanStatus.PUBLISHED.equals(plan.getStatus())) {
            throw new EventConflictException("Plan is not published");
        }

        List<Assignment> assignments = assignmentRepository.findByPlanIdWithDetailsOrderByTaskDetails(plan.getId());

        // Sort by task date, start time, task title, volunteer name, assignment id
        List<Assignment> sorted = new ArrayList<>(assignments);
        sorted.sort(Comparator
                .comparing((Assignment a) -> a.getTask().getTaskDate())
                .thenComparing(a -> a.getTask().getStartTime())
                .thenComparing(a -> a.getTask().getTitle())
                .thenComparing(a -> a.getVolunteer().getFullName(),
                        String.CASE_INSENSITIVE_ORDER.thenComparing(String::compareTo))
                .thenComparing(Assignment::getId));

        List<UUID> assignmentIds = sorted.stream().map(Assignment::getId).toList();
        Map<UUID, Feedback> feedbackMap = new HashMap<>();
        if (!assignmentIds.isEmpty()) {
            List<Feedback> feedbacks = feedbackRepository.findByAssignmentIdIn(assignmentIds);
            for (Feedback fb : feedbacks) {
                feedbackMap.put(fb.getAssignment().getId(), fb);
            }
        }

        List<FeedbackItemResponse> responses = new ArrayList<>();
        for (Assignment a : sorted) {
            Feedback fb = feedbackMap.get(a.getId());
            responses.add(new FeedbackItemResponse(
                    a.getId(),
                    a.getVolunteer().getId(),
                    a.getVolunteer().getFullName(),
                    a.getTask().getTitle(),
                    fb != null ? fb.getRating() : null,
                    fb != null ? fb.getComment() : null));
        }

        return responses;
    }

    @Transactional
    public FeedbackItemResponse upsertFeedback(UUID eventId, UUID assignmentId, UpsertFeedbackRequest req) {
        // Lock the event row first
        eventRepository.findWithLockById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found: " + eventId));

        Plan plan = planRepository.findByEventId(eventId)
                .orElseThrow(() -> new EventConflictException("Plan is not published"));

        if (!PlanStatus.PUBLISHED.equals(plan.getStatus())) {
            throw new EventConflictException("Plan is not published");
        }

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new EventNotFoundException("Assignment not found"));

        if (!plan.getId().equals(assignment.getPlan().getId())) {
            throw new EventNotFoundException("Assignment not found");
        }

        // Clean & validate comment
        String cleanComment = null;
        if (req.getComment() != null) {
            TextRules.rejectControlChars(req.getComment(), "comment");
            String trimmed = req.getComment().trim();
            if (!trimmed.isEmpty()) {
                cleanComment = trimmed;
            }
        }

        Feedback feedback = feedbackRepository.findByAssignmentId(assignmentId)
                .orElseGet(() -> new Feedback(assignment, req.getRating(), null));

        feedback.setRating(req.getRating());
        feedback.setComment(cleanComment);
        feedback.setUpdatedAt(Instant.now());

        Feedback saved = feedbackRepository.save(feedback);

        return new FeedbackItemResponse(
                assignment.getId(),
                assignment.getVolunteer().getId(),
                assignment.getVolunteer().getFullName(),
                assignment.getTask().getTitle(),
                saved.getRating(),
                saved.getComment());
    }
}
