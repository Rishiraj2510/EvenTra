package com.eventra.settings;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "settings")
public class Settings {

    @Id
    @Column(name = "only_row")
    private Boolean onlyRow = true;

    @Column(name = "weight_skill", nullable = false)
    private int weightSkill;

    @Column(name = "weight_experience", nullable = false)
    private int weightExperience;

    @Column(name = "weight_performance", nullable = false)
    private int weightPerformance;

    @Column(name = "weight_load", nullable = false)
    private int weightLoad;

    @Column(name = "weight_interest", nullable = false)
    private int weightInterest;

    @Column(name = "first_timer_bonus", nullable = false)
    private int firstTimerBonus;

    @Column(name = "default_max_hours", nullable = false)
    private int defaultMaxHours;

    public Settings() {
    }

    public Boolean isOnlyRow() {
        return onlyRow;
    }

    public Boolean getOnlyRow() {
        return onlyRow;
    }

    public void setOnlyRow(Boolean onlyRow) {
        this.onlyRow = onlyRow;
    }

    public int getWeightSkill() {
        return weightSkill;
    }

    public void setWeightSkill(int weightSkill) {
        this.weightSkill = weightSkill;
    }

    public int getWeightExperience() {
        return weightExperience;
    }

    public void setWeightExperience(int weightExperience) {
        this.weightExperience = weightExperience;
    }

    public int getWeightPerformance() {
        return weightPerformance;
    }

    public void setWeightPerformance(int weightPerformance) {
        this.weightPerformance = weightPerformance;
    }

    public int getWeightLoad() {
        return weightLoad;
    }

    public void setWeightLoad(int weightLoad) {
        this.weightLoad = weightLoad;
    }

    public int getWeightInterest() {
        return weightInterest;
    }

    public void setWeightInterest(int weightInterest) {
        this.weightInterest = weightInterest;
    }

    public int getFirstTimerBonus() {
        return firstTimerBonus;
    }

    public void setFirstTimerBonus(int firstTimerBonus) {
        this.firstTimerBonus = firstTimerBonus;
    }

    public int getDefaultMaxHours() {
        return defaultMaxHours;
    }

    public void setDefaultMaxHours(int defaultMaxHours) {
        this.defaultMaxHours = defaultMaxHours;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Settings settings = (Settings) o;
        return Objects.equals(onlyRow, settings.onlyRow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyRow);
    }
}

