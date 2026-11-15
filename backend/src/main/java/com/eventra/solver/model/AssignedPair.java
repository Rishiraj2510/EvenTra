package com.eventra.solver.model;

import com.eventra.scoring.model.FitResult;

import java.util.UUID;

/**
 * Immutable record representing an assignment of a volunteer to a task slot.
 *
 * @param volunteerId unique identifier of the assigned volunteer, non-null
 * @param taskId unique identifier of the task, non-null
 * @param slotNumber 1-based slot index within the task (1..headcount)
 * @param fit scoring result, non-null and feasible
 */
public record AssignedPair(
        UUID volunteerId,
        UUID taskId,
        int slotNumber,
        FitResult fit
) {
    public AssignedPair {
        if (volunteerId == null) {
            throw new IllegalArgumentException("volunteerId cannot be null");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("taskId cannot be null");
        }
        if (slotNumber < 1) {
            throw new IllegalArgumentException("slotNumber must start at 1, got: " + slotNumber);
        }
        if (fit == null) {
            throw new IllegalArgumentException("fit cannot be null");
        }
        if (!fit.feasible()) {
            throw new IllegalArgumentException("fit must be feasible");
        }
    }
}

