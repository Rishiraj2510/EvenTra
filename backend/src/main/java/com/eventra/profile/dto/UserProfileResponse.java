package com.eventra.profile.dto;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;

import java.util.List;

public class UserProfileResponse {

    private String email;
    private String fullName;
    private String phone;
    private String department;
    private Integer yearOfStudy;
    private List<SkillLevel> skills;
    private int pastEventsCount;
    private List<AvailabilitySlot> availability;
    private Integer maxHoursPerEvent;
    private List<String> interests;
    private String notificationPreference;

    public UserProfileResponse() {
    }

    public UserProfileResponse(String email, String fullName, String phone, String department,
                               Integer yearOfStudy, List<SkillLevel> skills, int pastEventsCount,
                               List<AvailabilitySlot> availability, Integer maxHoursPerEvent,
                               List<String> interests, String notificationPreference) {
        this.email = email;
        this.fullName = fullName;
        this.phone = phone;
        this.department = department;
        this.yearOfStudy = yearOfStudy;
        this.skills = skills;
        this.pastEventsCount = pastEventsCount;
        this.availability = availability;
        this.maxHoursPerEvent = maxHoursPerEvent;
        this.interests = interests;
        this.notificationPreference = notificationPreference;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
}
