package com.eventra.settings.dto;

public class SettingsResponse {

    private WeightsDto weights;
    private int firstTimerBonus;
    private int defaultMaxHours;

    public SettingsResponse() {}

    public SettingsResponse(WeightsDto weights, int firstTimerBonus, int defaultMaxHours) {
        this.weights = weights;
        this.firstTimerBonus = firstTimerBonus;
        this.defaultMaxHours = defaultMaxHours;
    }

    public WeightsDto getWeights() { return weights; }
    public void setWeights(WeightsDto weights) { this.weights = weights; }

    public int getFirstTimerBonus() { return firstTimerBonus; }
    public void setFirstTimerBonus(int firstTimerBonus) { this.firstTimerBonus = firstTimerBonus; }

    public int getDefaultMaxHours() { return defaultMaxHours; }
    public void setDefaultMaxHours(int defaultMaxHours) { this.defaultMaxHours = defaultMaxHours; }
}

