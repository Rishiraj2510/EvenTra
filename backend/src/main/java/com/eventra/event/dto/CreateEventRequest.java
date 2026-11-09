package com.eventra.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public class CreateEventRequest {

    @NotBlank(message = "Event name is required")
    @Size(max = 120, message = "Event name must not exceed 120 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    private Instant startDate;
    private Instant endDate;
    private String status;
    private Map<String, Object> certificateTemplate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getCertificateTemplate() { return certificateTemplate; }
    public void setCertificateTemplate(Map<String, Object> certificateTemplate) {
        this.certificateTemplate = certificateTemplate;
    }
}


