package com.eventra.plan.dto;

import java.util.UUID;

public record SkillGapDto(
        UUID taskId,
        String taskTitle,
        String missingSkill,
        long slotsUnfilled
) {
}
