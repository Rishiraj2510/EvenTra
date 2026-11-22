package com.eventra.plan.dto;

import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID eventId,
        String status,
        boolean stale,
        PlanSummaryDto summary,
        List<AssignmentDto> assignments,
        List<SkillGapDto> skillGaps
) {
}
