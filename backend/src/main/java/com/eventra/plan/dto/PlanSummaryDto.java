package com.eventra.plan.dto;

public record PlanSummaryDto(
        Double averageFit,
        long unqualifiedAssignments,
        String workloadSpread,
        long headcountFilled,
        long headcountRequired,
        long skillGapCount
) {
}
