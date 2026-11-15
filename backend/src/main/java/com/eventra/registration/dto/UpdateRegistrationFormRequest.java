package com.eventra.registration.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public class UpdateRegistrationFormRequest {

    private Instant opensAt;
    private Instant closesAt;
    private Integer maxResponses;

    @NotNull(message = "offeredSkills is required")
    private List<String> offeredSkills;

    @NotNull(message = "extraQuestions is required")
    private List<ExtraQuestionDto> extraQuestions;

    public UpdateRegistrationFormRequest() {
    }

    public Instant getOpensAt() {
        return opensAt;
    }

    public void setOpensAt(Instant opensAt) {
        this.opensAt = opensAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(Instant closesAt) {
        this.closesAt = closesAt;
    }

    public Integer getMaxResponses() {
        return maxResponses;
    }

    public void setMaxResponses(Integer maxResponses) {
        this.maxResponses = maxResponses;
    }

    public List<String> getOfferedSkills() {
        return offeredSkills;
    }

    public void setOfferedSkills(List<String> offeredSkills) {
        this.offeredSkills = offeredSkills;
    }

    public List<ExtraQuestionDto> getExtraQuestions() {
        return extraQuestions;
    }

    public void setExtraQuestions(List<ExtraQuestionDto> extraQuestions) {
        this.extraQuestions = extraQuestions;
    }
}
