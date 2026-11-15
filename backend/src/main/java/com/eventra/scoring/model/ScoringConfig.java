package com.eventra.scoring.model;

/**
 * Immutable configuration for the deterministic scoring engine.
 *
 * @param weights scoring factor weights summing to 100, non-null
 * @param firstTimerBonus bonus added to fit for first-time volunteers on non-critical tasks, unit: score points [0..20]
 * @param defaultMaxHours fallback maximum working hours if volunteer maxHours is &lt;= 0, unit: hours [1..24]
 */
public record ScoringConfig(
        ScoreWeights weights,
        int firstTimerBonus,
        int defaultMaxHours
) {
    public ScoringConfig {
        if (weights == null) {
            throw new IllegalArgumentException("weights cannot be null");
        }
        if (firstTimerBonus < 0 || firstTimerBonus > 20) {
            throw new IllegalArgumentException("firstTimerBonus must be between 0 and 20, got: " + firstTimerBonus);
        }
        if (defaultMaxHours < 1 || defaultMaxHours > 24) {
            throw new IllegalArgumentException("defaultMaxHours must be between 1 and 24, got: " + defaultMaxHours);
        }
    }
}
