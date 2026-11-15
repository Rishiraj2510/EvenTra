package com.eventra.scoring.model;

/**
 * Immutable record defining the weight distribution across scoring components.
 * Each individual weight is an integer percentage in range [0, 100], and the sum
 * of all five weights must equal exactly 100.
 *
 * @param skill weight allocated to volunteer skill matching, unit: percentage points [0..100]
 * @param experience weight allocated to volunteer experience factor, unit: percentage points [0..100]
 * @param performance weight allocated to volunteer rating performance factor, unit: percentage points [0..100]
 * @param load weight allocated to volunteer load factor, unit: percentage points [0..100]
 * @param interest weight allocated to volunteer interest alignment, unit: percentage points [0..100]
 */
public record ScoreWeights(
        int skill,
        int experience,
        int performance,
        int load,
        int interest
) {
    public ScoreWeights {
        if (skill < 0 || skill > 100) {
            throw new IllegalArgumentException("skill weight must be between 0 and 100, got: " + skill);
        }
        if (experience < 0 || experience > 100) {
            throw new IllegalArgumentException("experience weight must be between 0 and 100, got: " + experience);
        }
        if (performance < 0 || performance > 100) {
            throw new IllegalArgumentException("performance weight must be between 0 and 100, got: " + performance);
        }
        if (load < 0 || load > 100) {
            throw new IllegalArgumentException("load weight must be between 0 and 100, got: " + load);
        }
        if (interest < 0 || interest > 100) {
            throw new IllegalArgumentException("interest weight must be between 0 and 100, got: " + interest);
        }
        int sum = skill + experience + performance + load + interest;
        if (sum != 100) {
            throw new IllegalArgumentException("Weights must sum exactly to 100, got sum: " + sum);
        }
    }
}
