package com.eventra.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SettingsRequest {

    @NotNull(message = "Weights are required")
    @Valid
    private WeightsDto weights;

    @NotNull(message = "First timer bonus is required")
    @Min(value = 0, message = "First timer bonus must be between 0 and 20")
    @Max(value = 20, message = "First timer bonus must be between 0 and 20")
    private Integer firstTimerBonus;

    @NotNull(message = "Default max hours is required")
    @Min(value = 1, message = "Default max hours must be between 1 and 24")
    @Max(value = 24, message = "Default max hours must be between 1 and 24")
    private Integer defaultMaxHours;

    public SettingsRequest() {}

    public SettingsRequest(WeightsDto weights, Integer firstTimerBonus, Integer defaultMaxHours) {
        this.weights = weights;
        this.firstTimerBonus = firstTimerBonus;
        this.defaultMaxHours = defaultMaxHours;
    }


    public WeightsDto getWeights() { return weights; }
    public void setWeights(WeightsDto weights) { this.weights = weights; }

    public Integer getFirstTimerBonus() { return firstTimerBonus; }
    public void setFirstTimerBonus(Integer firstTimerBonus) { this.firstTimerBonus = firstTimerBonus; }

    public Integer getDefaultMaxHours() { return defaultMaxHours; }
    public void setDefaultMaxHours(Integer defaultMaxHours) { this.defaultMaxHours = defaultMaxHours; }
}

