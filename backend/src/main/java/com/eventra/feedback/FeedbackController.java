package com.eventra.feedback;

import com.eventra.feedback.dto.FeedbackItemResponse;
import com.eventra.feedback.dto.UpsertFeedbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<List<FeedbackItemResponse>> getFeedback(@PathVariable UUID eventId) {
        return ResponseEntity.ok(feedbackService.getFeedback(eventId));
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("hasRole('ORGANISER')")
    public ResponseEntity<FeedbackItemResponse> upsertFeedback(
            @PathVariable UUID eventId,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody UpsertFeedbackRequest req) {
        return ResponseEntity.ok(feedbackService.upsertFeedback(eventId, assignmentId, req));
    }
}

