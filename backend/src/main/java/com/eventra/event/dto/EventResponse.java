package com.eventra.event.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class EventResponse {

    private UUID id;
    private String name;
    private String description;
    private Instant startDate;
    private Instant endDate;
    private String status;
    private UUID createdBy;
    private Map<String, Object> certificateTemplate;
    private Instant createdAt;
    private Instant updatedAt;

    public EventResponse() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Map<String, Object> getCertificateTemplate() { return certificateTemplate; }
    public void setCertificateTemplate(Map<String, Object> certificateTemplate) {
        this.certificateTemplate = certificateTemplate;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

