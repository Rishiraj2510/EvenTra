package com.eventra.solver.model;

import java.util.UUID;

/**
 * Immutable record representing an unfilled portion of a task.
 *
 * @param taskId unique identifier of the task, non-null
 * @param slotsUnfilled number of unfilled volunteer slots, strictly positive
 * @param missingSkill rarest mandatory skill among input volunteers, or null if none missing
 */
public record UnfilledTask(
        UUID taskId,
        int slotsUnfilled,
        String missingSkill
) {
    public UnfilledTask {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId cannot be null");
        }
        if (slotsUnfilled <= 0) {
            throw new IllegalArgumentException("slotsUnfilled must be strictly positive, got: " + slotsUnfilled);
        }
    }
}

