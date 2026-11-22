package com.eventra.solver;

import com.eventra.scoring.ScoringService;
import com.eventra.scoring.model.FitResult;
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
 * Deterministic greedy baseline solver for volunteer-to-task assignment.
 * Assigns volunteers greedily per slot in canonical order by choosing the unused
 * feasible candidate with the highest fit score (breaking ties with smallest volunteer id).
 */
public final class GreedyBaseline {

    private GreedyBaseline() {
    }

    /**
     * Solves the assignment problem using a slot-by-slot greedy heuristic.
     *
     * @param volunteers candidate volunteers, non-null
     * @param tasks task requirements, non-null
     * @param config scoring weights and bonus configuration, non-null
     * @return greedy assignment result
     * @throws IllegalArgumentException on null arguments, null elements, duplicate ids, or size &gt; 500
     */
    public static SolverResult solve(
            List<SolverVolunteer> volunteers,
            List<SolverTask> tasks,
            ScoringConfig config
    ) {
        SolverSupport.validateInput(volunteers, tasks, config);

        List<SolverVolunteer> sortedVolunteers = SolverSupport.sortVolunteers(volunteers);
        List<SolverTask> sortedTasks = SolverSupport.sortTasks(tasks);
        List<SolverSupport.TaskSlot> slots = SolverSupport.expandSlots(sortedTasks);

        if (sortedTasks.isEmpty()) {
            return new SolverResult(List.of(), List.of(), 0L);
        }
        if (sortedVolunteers.isEmpty()) {
            List<UnfilledTask> unfilled = SolverSupport.buildUnfilled(sortedTasks, Map.of(), volunteers);
            return new SolverResult(List.of(), unfilled, 0L);
        }

        Set<UUID> usedVolunteerIds = new HashSet<>();
        List<AssignedPair> assignments = new ArrayList<>();
        Map<UUID, Integer> taskFilledCount = new HashMap<>();
        long totalCost = 0L;

        for (SolverSupport.TaskSlot ts : slots) {
            SolverVolunteer bestVolunteer = null;
            FitResult bestFit = null;

            for (SolverVolunteer v : sortedVolunteers) {
                if (usedVolunteerIds.contains(v.id())) {
                    continue;
                }
                FitResult fit = ScoringService.score(v, ts.task(), config);
                if (!fit.feasible()) {
                    continue;
                }

                if (bestVolunteer == null) {
                    bestVolunteer = v;
                    bestFit = fit;
                } else if (fit.fit() > bestFit.fit()) {
                    bestVolunteer = v;
                    bestFit = fit;
                } else if (Double.compare(fit.fit(), bestFit.fit()) == 0) {
                    if (v.id().compareTo(bestVolunteer.id()) < 0) {
                        bestVolunteer = v;
                        bestFit = fit;
                    }
                }
            }

            if (bestVolunteer != null) {
                usedVolunteerIds.add(bestVolunteer.id());
                assignments.add(new AssignedPair(bestVolunteer.id(), ts.task().id(), ts.slotNumber(), bestFit));
                taskFilledCount.put(ts.task().id(), taskFilledCount.getOrDefault(ts.task().id(), 0) + 1);
                totalCost += SolverSupport.cost(bestFit);
            }
        }

        List<UnfilledTask> unfilled = SolverSupport.buildUnfilled(sortedTasks, taskFilledCount, volunteers);
        return new SolverResult(assignments, unfilled, totalCost);
    }
}
