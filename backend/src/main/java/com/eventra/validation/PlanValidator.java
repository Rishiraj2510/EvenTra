package com.eventra.validation;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import com.eventra.solver.model.AssignedPair;
import com.eventra.solver.model.SolverResult;
import com.eventra.solver.model.UnfilledTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Independent validator for solver-generated assignment plans.
 * Re-implements constraint checks without calling ScoringService or the solver.
 */
public final class PlanValidator {

    private PlanValidator() {
    }

    /**
     * Validates an assignment result against input volunteers, tasks, and
     * configuration.
     *
     * @param volunteers list of input volunteers
     * @param tasks      list of input tasks
     * @param config     scoring configuration
     * @param result     solver result to validate
     * @return list of violation descriptions, empty if valid
     */
    public static List<String> validate(
            List<SolverVolunteer> volunteers,
            List<SolverTask> tasks,
            ScoringConfig config,
            SolverResult result) {
        List<String> violations = new ArrayList<>();
        if (result == null) {
            violations.add("Result cannot be null");
            return violations;
        }
        if (config == null) {
            violations.add("Config cannot be null");
            return violations;
        }

        Map<UUID, SolverVolunteer> volunteerMap = new HashMap<>();
        if (volunteers != null) {
            for (SolverVolunteer v : volunteers) {
                if (v != null && v.id() != null) {
                    volunteerMap.put(v.id(), v);
                }
            }
        }

        Map<UUID, SolverTask> taskMap = new HashMap<>();
        if (tasks != null) {
            for (SolverTask t : tasks) {
                if (t != null && t.id() != null) {
                    taskMap.put(t.id(), t);
                }
            }
        }

        Set<UUID> usedVolunteers = new HashSet<>();
        Map<UUID, Integer> taskAssignmentCount = new HashMap<>();
        Map<UUID, Set<Integer>> taskSlotNumbers = new HashMap<>();

        for (AssignedPair pair : result.assignments()) {
            if (pair == null) {
                violations.add("Null assignment pair");
                continue;
            }

            if (!volunteerMap.containsKey(pair.volunteerId())) {
                violations.add("Unknown volunteer ID: " + pair.volunteerId());
            }
            if (!taskMap.containsKey(pair.taskId())) {
                violations.add("Unknown task ID: " + pair.taskId());
            }

            if (!usedVolunteers.add(pair.volunteerId())) {
                violations.add("Volunteer assigned multiple times: " + pair.volunteerId());
            }

            if (pair.fit() == null || !pair.fit().feasible()) {
                violations.add("Assignment fit is not feasible for task: " + pair.taskId());
            }

            if (taskMap.containsKey(pair.taskId())) {
                SolverTask t = taskMap.get(pair.taskId());
                if (pair.slotNumber() < 1 || pair.slotNumber() > t.headcount()) {
                    violations.add("Slot number out of range for task: " + t.id());
                }
                Set<Integer> slots = taskSlotNumbers.computeIfAbsent(t.id(), k -> new HashSet<>());
                if (!slots.add(pair.slotNumber())) {
                    violations.add("Duplicate slot number for task: " + t.id());
                }
                taskAssignmentCount.put(t.id(), taskAssignmentCount.getOrDefault(t.id(), 0) + 1);
            }

            if (volunteerMap.containsKey(pair.volunteerId()) && taskMap.containsKey(pair.taskId())) {
                SolverVolunteer v = volunteerMap.get(pair.volunteerId());
                SolverTask t = taskMap.get(pair.taskId());

                // Mandatory skills check (never echo skill text)
                if (t.mandatorySkills() != null) {
                    for (String mandatory : t.mandatorySkills()) {
                        if (mandatory != null) {
                            String trimmedMandatory = mandatory.trim();
                            boolean hasSkill = false;
                            if (v.skills() != null) {
                                for (SkillLevel sl : v.skills()) {
                                    if (sl != null && sl.getName() != null
                                            && sl.getName().trim().equalsIgnoreCase(trimmedMandatory)) {
                                        hasSkill = true;
                                        break;
                                    }
                                }
                            }
                            if (!hasSkill) {
                                violations.add("Volunteer missing mandatory skill for task: " + t.id());
                                break;
                            }
                        }
                    }
                }

                // Availability check
                String expectedDay = t.date().getDayOfWeek().name();
                int s = t.startTime().toSecondOfDay();
                String expectedSlot;
                if (5 * 3600 <= s && s < 12 * 3600) {
                    expectedSlot = ModelValues.SLOTS.get(0);
                } else if (12 * 3600 <= s && s < 17 * 3600) {
                    expectedSlot = ModelValues.SLOTS.get(1);
                } else {
                    expectedSlot = ModelValues.SLOTS.get(2);
                }

                boolean available = false;
                if (v.availability() != null) {
                    for (AvailabilitySlot slot : v.availability()) {
                        if (slot != null && slot.getDay() != null && slot.getSlot() != null) {
                            if (slot.getDay().trim().equalsIgnoreCase(expectedDay)
                                    && slot.getSlot().trim().equalsIgnoreCase(expectedSlot)) {
                                available = true;
                                break;
                            }
                        }
                    }
                }
                if (!available) {
                    violations.add("Volunteer unavailable for task: " + t.id());
                }

                // Duration check
                long durSec = Math.floorMod((long) t.endTime().toSecondOfDay() - t.startTime().toSecondOfDay(), 86400L);
                int defaultMaxHours = (config.defaultMaxHours() > 0) ? config.defaultMaxHours() : 8;
                long maxHours = v.maxHours() > 0 ? v.maxHours() : defaultMaxHours;
                if (durSec > maxHours * 3600L) {
                    violations.add("Volunteer over hours for task: " + t.id());
                }
            }
        }

        if (tasks != null) {
            for (SolverTask t : tasks) {
                if (t != null) {
                    int assigned = taskAssignmentCount.getOrDefault(t.id(), 0);
                    if (assigned > t.headcount()) {
                        violations.add("Task exceeds headcount: " + t.id());
                    }
                }
            }
        }

        Map<UUID, Integer> unfilledCounts = new HashMap<>();
        Set<UUID> seenUnfilledTaskIds = new HashSet<>();
        for (UnfilledTask ut : result.unfilled()) {
            if (ut == null) {
                violations.add("Null unfilled task entry");
                continue;
            }
            if (!taskMap.containsKey(ut.taskId())) {
                violations.add("Unknown task ID in unfilled: " + ut.taskId());
            }
            if (!seenUnfilledTaskIds.add(ut.taskId())) {
                violations.add("Duplicate unfilled entry for task: " + ut.taskId());
            }
            unfilledCounts.put(ut.taskId(), unfilledCounts.getOrDefault(ut.taskId(), 0) + ut.slotsUnfilled());
        }

        if (tasks != null) {
            for (SolverTask t : tasks) {
                if (t != null) {
                    int assigned = taskAssignmentCount.getOrDefault(t.id(), 0);
                    int unfilled = unfilledCounts.getOrDefault(t.id(), 0);
                    if (assigned <= t.headcount() && assigned + unfilled != t.headcount()) {
                        violations.add("Headcount mismatch for task: " + t.id());
                    }
                }
            }
        }

        return violations;
    }
}
