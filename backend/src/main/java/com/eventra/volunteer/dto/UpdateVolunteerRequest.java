package com.eventra.volunteer.dto;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateVolunteerRequest {

    @Size(min = 2, max = 100, message = "fullName must be between 2 and 100 characters")
    private String fullName;

    @Size(max = 20, message = "phone must not exceed 20 characters")
    private String phone;

    @Size(max = 100, message = "department must not exceed 100 characters")
    private String department;

    @Min(value = ModelValues.MIN_YEAR_OF_STUDY, message = "yearOfStudy must be between 1 and 4")
    @Max(value = ModelValues.MAX_YEAR_OF_STUDY, message = "yearOfStudy must be between 1 and 4")
    private Integer yearOfStudy;

    private List<SkillLevel> skills;

    @Min(value = 0, message = "pastEventsCount must be 0 or more")
    private Integer pastEventsCount;

    private List<AvailabilitySlot> availability;

    @Min(value = 1, message = "maxHoursPerEvent must be between 1 and 24")
    @Max(value = 24, message = "maxHoursPerEvent must be between 1 and 24")
    private Integer maxHoursPerEvent;

    private List<String> interests;

    private String notificationPreference;

    private String status;

    public UpdateVolunteerRequest() {
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

    public Integer getPastEventsCount() {
        return pastEventsCount;
    }

    public void setPastEventsCount(Integer pastEventsCount) {
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
}
