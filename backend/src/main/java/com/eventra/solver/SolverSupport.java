package com.eventra.solver;

import com.eventra.model.SkillLevel;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import com.eventra.solver.model.UnfilledTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Package-private utility class providing shared validation, canonical sorting,
 * slot expansion, cost calculation, and unfilled list building for solvers.
 */
final class SolverSupport {

    private SolverSupport() {
    }

    record TaskSlot(SolverTask task, int slotNumber) {
    }

    /**
     * Validates input volunteers, tasks, and scoring configuration against null checks,
     * duplicate ids, and size caps.
     *
     * @param volunteers candidate volunteers, non-null
     * @param tasks task requirements, non-null
     * @param config scoring weights and bonus configuration, non-null
     * @throws IllegalArgumentException on null arguments, null elements, duplicate ids, or size &gt; 500
     */
    static void validateInput(
            List<SolverVolunteer> volunteers,
            List<SolverTask> tasks,
            ScoringConfig config
    ) {
        if (volunteers == null) {
            throw new IllegalArgumentException("volunteers cannot be null");
        }
        if (tasks == null) {
            throw new IllegalArgumentException("tasks cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        Set<UUID> volunteerIds = new HashSet<>();
        for (SolverVolunteer v : volunteers) {
            if (v == null) {
                throw new IllegalArgumentException("volunteer element cannot be null");
            }
            if (!volunteerIds.add(v.id())) {
                throw new IllegalArgumentException("duplicate volunteer id: " + v.id());
            }
        }

        long totalSlotsCount = 0L;
        Set<UUID> taskIds = new HashSet<>();
        for (SolverTask t : tasks) {
            if (t == null) {
                throw new IllegalArgumentException("task element cannot be null");
            }
            if (!taskIds.add(t.id())) {
                throw new IllegalArgumentException("duplicate task id: " + t.id());
            }
            totalSlotsCount += t.headcount();
        }

        if (Math.max((long) volunteers.size(), totalSlotsCount) > AssignmentSolver.MAX_SIZE) {
            throw new IllegalArgumentException("max(volunteers, slots) exceeds 500");
        }
    }

    /**
     * Sorts volunteers canonically by volunteer ID.
     */
    static List<SolverVolunteer> sortVolunteers(List<SolverVolunteer> volunteers) {
        List<SolverVolunteer> sorted = new ArrayList<>(volunteers);
        sorted.sort(Comparator.comparing(SolverVolunteer::id));
        return sorted;
    }

    /**
     * Sorts tasks canonically by date, start time, and task ID.
     */
    static List<SolverTask> sortTasks(List<SolverTask> tasks) {
        List<SolverTask> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparing(SolverTask::date)
                .thenComparing(SolverTask::startTime)
                .thenComparing(SolverTask::id));
        return sorted;
    }

    /**
     * Expands canonically sorted tasks into task slots in canonical order.
     */
    static List<TaskSlot> expandSlots(List<SolverTask> sortedTasks) {
        List<TaskSlot> slots = new ArrayList<>();
        for (SolverTask t : sortedTasks) {
            for (int s = 1; s <= t.headcount(); s++) {
                slots.add(new TaskSlot(t, s));
            }
        }
        return slots;
    }

    /**
     * Computes the integer cost for a feasible fit result.
     * cost(FitResult) = Math.round((100.0 - fit) * 1000.0)
     */
    static long cost(FitResult fit) {
        return Math.round((100.0 - fit.fit()) * 1000.0);
    }

    /**
     * Builds the list of unfilled tasks in canonical order.
     */
    static List<UnfilledTask> buildUnfilled(
            List<SolverTask> sortedTasks,
            Map<UUID, Integer> taskFilledCount,
            List<SolverVolunteer> volunteers
    ) {
        List<UnfilledTask> unfilled = new ArrayList<>();
        for (SolverTask t : sortedTasks) {
            int filled = (taskFilledCount != null) ? taskFilledCount.getOrDefault(t.id(), 0) : 0;
            int unfilledSlots = t.headcount() - filled;
            if (unfilledSlots > 0) {
                String missing = computeMissingSkill(t, volunteers);
                unfilled.add(new UnfilledTask(t.id(), unfilledSlots, missing));
            }
        }
        return unfilled;
    }

    /**
     * Determines the rarest mandatory skill for an unfilled task among input volunteers.
     */
    static String computeMissingSkill(SolverTask task, List<SolverVolunteer> volunteers) {
        if (task.mandatorySkills() == null || task.mandatorySkills().isEmpty()) {
            return null;
        }

        List<String> distinctMandatory = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : task.mandatorySkills()) {
            if (s != null) {
                String trimmed = s.trim();
                if (seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                    distinctMandatory.add(trimmed);
                }
            }
        }
        if (distinctMandatory.isEmpty()) {
            return null;
        }

        String rarestSkill = null;
        int minVolunteers = Integer.MAX_VALUE;

        for (String skill : distinctMandatory) {
            int count = 0;
            if (volunteers != null) {
                for (SolverVolunteer v : volunteers) {
                    if (v == null || v.skills() == null) {
                        continue;
                    }
                    boolean holds = false;
                    for (SkillLevel sl : v.skills()) {
                        if (sl != null && sl.getName() != null && sl.getName().trim().equalsIgnoreCase(skill)) {
                            holds = true;
                            break;
                        }
                    }
                    if (holds) {
                        count++;
                    }
                }
            }

            if (count < minVolunteers) {
                minVolunteers = count;
                rarestSkill = skill;
            } else if (count == minVolunteers) {
                if (rarestSkill == null || skill.compareToIgnoreCase(rarestSkill) < 0) {
                    rarestSkill = skill;
                }
            }
        }

        if (minVolunteers >= 1) {
            return null;
        }
        return rarestSkill;
    }
}

