package com.eventra.registration.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RegistrationFormViewResponse {

    private UUID eventId;
    private String state;
    private Instant opensAt;
    private Instant closesAt;
    private List<String> offeredSkills;
    private List<ExtraQuestionDto> extraQuestions;

    public RegistrationFormViewResponse() {
    }

    public RegistrationFormViewResponse(UUID eventId, String state, Instant opensAt, Instant closesAt,
                                        List<String> offeredSkills, List<ExtraQuestionDto> extraQuestions) {
        this.eventId = eventId;
        this.state = state;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.offeredSkills = offeredSkills;
        this.extraQuestions = extraQuestions;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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
