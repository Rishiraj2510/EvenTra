package com.eventra.profile.dto;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class UpdateUserProfileRequest {

    @NotNull(message = "fullName is required")
    private String fullName;

    @NotNull(message = "phone is required")
    private String phone;

    @NotNull(message = "department is required")
    private String department;

    @NotNull(message = "yearOfStudy is required")
    @Min(value = ModelValues.MIN_YEAR_OF_STUDY, message = "yearOfStudy must be between 1 and 4")
    @Max(value = ModelValues.MAX_YEAR_OF_STUDY, message = "yearOfStudy must be between 1 and 4")
    private Integer yearOfStudy;

    @NotNull(message = "skills is required")
    private List<SkillLevel> skills;

    @NotNull(message = "availability is required")
    private List<AvailabilitySlot> availability;

    @NotNull(message = "maxHoursPerEvent is required")
    @Min(value = ModelValues.MIN_HOURS_PER_EVENT, message = "maxHoursPerEvent must be between 1 and 24")
    @Max(value = ModelValues.MAX_HOURS_PER_EVENT, message = "maxHoursPerEvent must be between 1 and 24")
    private Integer maxHoursPerEvent;

    @NotNull(message = "interests is required")
    private List<String> interests;

    @NotNull(message = "notificationPreference is required")
    private String notificationPreference;

    public UpdateUserProfileRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
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
}
