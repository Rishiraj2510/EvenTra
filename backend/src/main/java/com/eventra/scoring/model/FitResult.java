package com.eventra.scoring.model;

import java.util.List;

/**
 * Immutable evaluation result for a volunteer-task pair.
 *
 * @param feasible true if all hard constraints pass, false if any constraint fails
 * @param infeasibleReason human-readable failure reason naming the first failing constraint, null when feasible
 * @param fit total calculated fit score in range [0.0, 100.0], strictly 0.0 when infeasible
 * @param breakdown detailed factor scores in range [0.0, 1.0], null when infeasible
 * @param flags informational labels (e.g., "First-timer", "Critical task"), non-null unmodifiable copy
 */
public record FitResult(
        boolean feasible,
        String infeasibleReason,
        double fit,
        FitBreakdown breakdown,
        List<String> flags
) {
    public FitResult {
        if (flags == null) {
            throw new IllegalArgumentException("flags cannot be null");
        }
        for (String flag : flags) {
            if (flag == null) {
                throw new IllegalArgumentException("flag element cannot be null");
            }
        }
        flags = List.copyOf(flags);

        if (feasible) {
            if (infeasibleReason != null) {
                throw new IllegalArgumentException("infeasibleReason must be null when feasible");
            }
            if (Double.isNaN(fit) || fit < 0.0 || fit > 100.0) {
                throw new IllegalArgumentException("fit must be between 0.0 and 100.0 when feasible, got: " + fit);
            }
            if (breakdown == null) {
                throw new IllegalArgumentException("breakdown cannot be null when feasible");
            }
        } else {
            if (infeasibleReason == null) {
                throw new IllegalArgumentException("infeasibleReason cannot be null when infeasible");
            }
            if (fit != 0.0) {
                throw new IllegalArgumentException("fit must be exactly 0.0 when infeasible, got: " + fit);
            }
            if (breakdown != null) {
                throw new IllegalArgumentException("breakdown must be null when infeasible");
            }
        }
    }
}
