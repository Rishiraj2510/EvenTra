package com.eventra.plan.dto;

import java.util.List;
import java.util.UUID;

public record AssignmentDto(
        UUID id,
        UUID taskId,
        String taskTitle,
        UUID volunteerId,
        String volunteerName,
        Double fit,
        BreakdownDto breakdown,
        String reason,
        List<String> flags,
        boolean locked
) {
}
