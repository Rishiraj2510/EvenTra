package com.eventra.registration.dto;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class VolunteerRegistrationResponse {

    private UUID id;
    private UUID eventId;
    private UUID userId;
    private String fullName;
    private String email;
    private String phone;
    private String department;
    private Integer yearOfStudy;
    private List<SkillLevel> skills;
    private int pastEventsCount;
    private List<AvailabilitySlot> availability;
    private Integer maxHoursPerEvent;
    private List<String> interests;
    private String notificationPreference;
    private String status;
    private Instant registeredAt;

    public VolunteerRegistrationResponse() {
    }

    public VolunteerRegistrationResponse(UUID id, UUID eventId, UUID userId, String fullName,
                                         String email, String phone, String department,
                                         Integer yearOfStudy, List<SkillLevel> skills,
                                         int pastEventsCount, List<AvailabilitySlot> availability,
                                         Integer maxHoursPerEvent, List<String> interests,
                                         String notificationPreference, String status,
                                         Instant registeredAt) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.department = department;
        this.yearOfStudy = yearOfStudy;
        this.skills = skills;
        this.pastEventsCount = pastEventsCount;
        this.availability = availability;
        this.maxHoursPerEvent = maxHoursPerEvent;
        this.interests = interests;
        this.notificationPreference = notificationPreference;
        this.status = status;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(Integer yearOfStudy) {
        this.yearOfStudy = yearOfStudy;
    }

    public List<SkillLevel> getSkills() {
        return skills;
    }

    public void setSkills(List<SkillLevel> skills) {
        this.skills = skills;
    }

    public int getPastEventsCount() {
        return pastEventsCount;
    }

    public void setPastEventsCount(int pastEventsCount) {
        this.pastEventsCount = pastEventsCount;
    }

    public List<AvailabilitySlot> getAvailability() {
        return availability;
    }

    public void setAvailability(List<AvailabilitySlot> availability) {
        this.availability = availability;
    }

    public Integer getMaxHoursPerEvent() {
        return maxHoursPerEvent;
    }

    public void setMaxHoursPerEvent(Integer maxHoursPerEvent) {
        this.maxHoursPerEvent = maxHoursPerEvent;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public String getNotificationPreference() {
        return notificationPreference;
    }

    public void setNotificationPreference(String notificationPreference) {
        this.notificationPreference = notificationPreference;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }
}
