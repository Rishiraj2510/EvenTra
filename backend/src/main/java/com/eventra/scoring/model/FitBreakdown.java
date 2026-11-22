package com.eventra.scoring.model;

/**
 * Immutable record capturing the normalized sub-scores for each scoring dimension.
 * Each factor must be in the closed range [0.0, 1.0].
 *
 * @param skill normalized skill match factor, unit: ratio [0.0..1.0]
 * @param experience normalized past events experience factor, unit: ratio [0.0..1.0]
 * @param performance normalized rating performance factor, unit: ratio [0.0..1.0]
 * @param load normalized workload factor, unit: ratio [0.0..1.0]
 * @param interest normalized interest match factor, unit: ratio [0.0..1.0]
 */
public record FitBreakdown(
        double skill,
        double experience,
        double performance,
        double load,
        double interest
) {
    public FitBreakdown {
        if (Double.isNaN(skill) || skill < 0.0 || skill > 1.0) {
            throw new IllegalArgumentException("skill factor must be between 0.0 and 1.0, got: " + skill);
        }
        if (Double.isNaN(experience) || experience < 0.0 || experience > 1.0) {
            throw new IllegalArgumentException("experience factor must be between 0.0 and 1.0, got: " + experience);
        }
        if (Double.isNaN(performance) || performance < 0.0 || performance > 1.0) {
            throw new IllegalArgumentException("performance factor must be between 0.0 and 1.0, got: " + performance);
        }
        if (Double.isNaN(load) || load < 0.0 || load > 1.0) {
            throw new IllegalArgumentException("load factor must be between 0.0 and 1.0, got: " + load);
        }
        if (Double.isNaN(interest) || interest < 0.0 || interest > 1.0) {
            throw new IllegalArgumentException("interest factor must be between 0.0 and 1.0, got: " + interest);
        }
    }
}
