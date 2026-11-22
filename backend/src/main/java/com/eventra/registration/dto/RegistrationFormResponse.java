package com.eventra.registration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegistrationFormResponse {

    private UUID eventId;
    private String status;
    private Instant opensAt;
    private Instant closesAt;
    private Integer maxResponses;
    private List<String> offeredSkills;
    private List<ExtraQuestionDto> extraQuestions;
    private long responseCount;

    public RegistrationFormResponse() {
    }

    public RegistrationFormResponse(UUID eventId, String status, Instant opensAt, Instant closesAt,
                                    Integer maxResponses, List<String> offeredSkills,
                                    List<ExtraQuestionDto> extraQuestions, long responseCount) {
        this.eventId = eventId;
        this.status = status;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.maxResponses = maxResponses;
        this.offeredSkills = offeredSkills;
        this.extraQuestions = extraQuestions;
        this.responseCount = responseCount;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public long getResponseCount() {
        return responseCount;
    }

    public void setResponseCount(long responseCount) {
        this.responseCount = responseCount;
    }
}
