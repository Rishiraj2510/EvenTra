package com.eventra.solver.model;

import java.util.List;

/**
 * Immutable record representing the complete result of an assignment solver execution.
 *
 * @param assignments list of assigned volunteer-task slot pairs, non-null
 * @param unfilled list of unfilled tasks, non-null
 * @param totalCost sum of costs for all real assignments
 */
public record SolverResult(
        List<AssignedPair> assignments,
        List<UnfilledTask> unfilled,
        long totalCost
) {
    public SolverResult {
        if (assignments == null) {
            throw new IllegalArgumentException("assignments cannot be null");
        }
        for (AssignedPair pair : assignments) {
            if (pair == null) {
                throw new IllegalArgumentException("assignment element cannot be null");
            }
        }
        if (unfilled == null) {
            throw new IllegalArgumentException("unfilled cannot be null");
        }
        for (UnfilledTask task : unfilled) {
            if (task == null) {
                throw new IllegalArgumentException("unfilled element cannot be null");
            }
        }
        assignments = List.copyOf(assignments);
        unfilled = List.copyOf(unfilled);
    }
}

