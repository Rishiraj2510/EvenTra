package com.eventra.plan.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BreakdownDto(
        Double skill,
        Double experience,
        Double performance,
        Double load,
        Double interest
) {
    public static BreakdownDto fromScores(
            BigDecimal skillScore,
            BigDecimal experienceScore,
            BigDecimal performanceScore,
            BigDecimal loadScore,
            BigDecimal interestScore
    ) {
        return new BreakdownDto(
                toNormalizedDouble(skillScore),
                toNormalizedDouble(experienceScore),
                toNormalizedDouble(performanceScore),
                toNormalizedDouble(loadScore),
                toNormalizedDouble(interestScore)
        );
    }

    private static Double toNormalizedDouble(BigDecimal score) {
        if (score == null) return 0.0;
        // score is in [0, 100], normalize to [0.0, 1.0] and round to 2 decimals or exact double
        return score.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP).doubleValue();
    }
}
