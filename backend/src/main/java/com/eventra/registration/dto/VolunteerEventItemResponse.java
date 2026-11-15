package com.eventra.registration.dto;

import java.time.Instant;
import java.util.UUID;

public class VolunteerEventItemResponse {

    private UUID id;
    private String name;
    private String description;
    private Instant startDate;
    private Instant endDate;
    private String state;

    public VolunteerEventItemResponse() {
    }

    public VolunteerEventItemResponse(UUID id, String name, String description, Instant startDate, Instant endDate, String state) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.state = state;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public void setEndDate(Instant endDate) {
        this.endDate = endDate;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
