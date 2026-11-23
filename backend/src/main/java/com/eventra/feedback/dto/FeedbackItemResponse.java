package com.eventra.feedback.dto;

import java.util.UUID;

public record FeedbackItemResponse(
        UUID assignmentId,
        UUID volunteerId,
        String volunteerName,
        String taskTitle,
        Short rating,
        String comment
) {
}

