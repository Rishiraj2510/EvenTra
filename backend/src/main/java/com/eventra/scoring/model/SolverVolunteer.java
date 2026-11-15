package com.eventra.scoring.model;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;

import java.util.List;
import java.util.UUID;

/**
 * Immutable record representing a volunteer candidate for task assignment.
 *
 * @param id volunteer unique identifier, non-null
 * @param name volunteer full name, non-null
 * @param skills list of skills and proficiency levels, non-null unmodifiable copy
 * @param pastEventsCount count of past events attended, non-negative integer
 * @param averageRating volunteer average rating in range [1.0, 5.0], nullable (null indicates unrated)
 * @param availability list of day and time slot availabilities, non-null unmodifiable copy
 * @param maxHours maximum hours volunteer can work, if &lt;= 0 defaultMaxHours from config is used
 * @param interests list of interest tags or keywords, non-null unmodifiable copy
 */
public record SolverVolunteer(
        UUID id,
        String name,
        List<SkillLevel> skills,
        int pastEventsCount,
        Double averageRating,
        List<AvailabilitySlot> availability,
        int maxHours,
        List<String> interests
) {
    public SolverVolunteer {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        if (skills == null) {
            throw new IllegalArgumentException("skills cannot be null");
        }
        for (SkillLevel skill : skills) {
            if (skill == null) {
                throw new IllegalArgumentException("skill element cannot be null");
            }
        }
        if (pastEventsCount < 0) {
            throw new IllegalArgumentException("pastEventsCount cannot be negative, got: " + pastEventsCount);
        }
        if (averageRating != null && (averageRating < 1.0 || averageRating > 5.0)) {
            throw new IllegalArgumentException("averageRating must be in range [1.0, 5.0], got: " + averageRating);
        }
        if (availability == null) {
            throw new IllegalArgumentException("availability cannot be null");
        }
        for (AvailabilitySlot slot : availability) {
            if (slot == null) {
                throw new IllegalArgumentException("availability slot element cannot be null");
            }
        }
        if (interests == null) {
            throw new IllegalArgumentException("interests cannot be null");
        }
        for (String interest : interests) {
            if (interest == null) {
                throw new IllegalArgumentException("interest element cannot be null");
            }
        }
        skills = List.copyOf(skills);
        availability = List.copyOf(availability);
        interests = List.copyOf(interests);
    }
}
