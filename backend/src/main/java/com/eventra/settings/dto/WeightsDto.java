package com.eventra.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class WeightsDto {

    @NotNull(message = "Skill weight is required")
    @Min(value = 0, message = "Skill weight must be >= 0")
    @Max(value = 100, message = "Skill weight must be <= 100")
    private Integer skill;

    @NotNull(message = "Experience weight is required")
    @Min(value = 0, message = "Experience weight must be >= 0")
    @Max(value = 100, message = "Experience weight must be <= 100")
    private Integer experience;

    @NotNull(message = "Performance weight is required")
    @Min(value = 0, message = "Performance weight must be >= 0")
    @Max(value = 100, message = "Performance weight must be <= 100")
    private Integer performance;

    @NotNull(message = "Load weight is required")
    @Min(value = 0, message = "Load weight must be >= 0")
    @Max(value = 100, message = "Load weight must be <= 100")
    private Integer load;

    @NotNull(message = "Interest weight is required")
    @Min(value = 0, message = "Interest weight must be >= 0")
    @Max(value = 100, message = "Interest weight must be <= 100")
    private Integer interest;

    public WeightsDto() {}

    public WeightsDto(Integer skill, Integer experience, Integer performance, Integer load, Integer interest) {
        this.skill = skill;
        this.experience = experience;
        this.performance = performance;
        this.load = load;
        this.interest = interest;
    }


    public Integer getSkill() { return skill; }
    public void setSkill(Integer skill) { this.skill = skill; }

    public Integer getExperience() { return experience; }
    public void setExperience(Integer experience) { this.experience = experience; }

    public Integer getPerformance() { return performance; }
    public void setPerformance(Integer performance) { this.performance = performance; }

    public Integer getLoad() { return load; }
    public void setLoad(Integer load) { this.load = load; }

    public Integer getInterest() { return interest; }
    public void setInterest(Integer interest) { this.interest = interest; }
}

