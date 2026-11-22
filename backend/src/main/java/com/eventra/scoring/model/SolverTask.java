package com.eventra.scoring.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Immutable record representing a task candidate for solver assignment.
 * endTime before startTime means the task runs overnight; endTime equal to startTime means zero duration.
 *
 * @param id task unique identifier, non-null
 * @param title task title, non-null
 * @param date task execution date, non-null
 * @param startTime task starting time, non-null
 * @param endTime task ending time, non-null
 * @param headcount required number of volunteers, strictly positive integer (&gt;= 1)
 * @param mandatorySkills list of mandatory skills required for the task, non-null unmodifiable copy
 * @param preferredSkills list of preferred skills for the task, non-null unmodifiable copy
 * @param critical true if task is critical (triggers weight rescaling in scoring), false otherwise
 */
public record SolverTask(
        UUID id,
        String title,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        int headcount,
        List<String> mandatorySkills,
        List<String> preferredSkills,
        boolean critical
) {
    public SolverTask {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (title == null) {
            throw new IllegalArgumentException("title cannot be null");
        }
        if (date == null) {
            throw new IllegalArgumentException("date cannot be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime cannot be null");
        }
        if (endTime == null) {
            throw new IllegalArgumentException("endTime cannot be null");
        }
        if (headcount <= 0) {
            throw new IllegalArgumentException("headcount must be strictly positive, got: " + headcount);
        }
        if (mandatorySkills == null) {
            throw new IllegalArgumentException("mandatorySkills cannot be null");
        }
        for (String skill : mandatorySkills) {
            if (skill == null) {
                throw new IllegalArgumentException("mandatory skill element cannot be null");
            }
        }
        if (preferredSkills == null) {
            throw new IllegalArgumentException("preferredSkills cannot be null");
        }
        for (String skill : preferredSkills) {
            if (skill == null) {
                throw new IllegalArgumentException("preferred skill element cannot be null");
            }
        }
        mandatorySkills = List.copyOf(mandatorySkills);
        preferredSkills = List.copyOf(preferredSkills);
    }
}
