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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure-Java deterministic optimal assignment solver for matching volunteers to
 * tasks.
 *
 * Formulation and Guarantees:
 * <ul>
 * <li>Each real volunteer-task pair has a fit score in [0.0, 100.0]. For
 * feasible pairs,
 * the integer cost is {@code Math.round((100.0 - fit) * 1000)}, which lies in
 * [0, 100,000].</li>
 * <li>For infeasible pairs, an artificial penalty of
 * {@code BIG_M = 1_000_000_000_000L} (10^12) is used.
 * Because the problem size is at most 500, any valid sum of real costs cannot
 * exceed
 * 500 * 100,000 = 50,000,000. Since BIG_M is 20,000 times larger than the
 * theoretical maximum
 * sum of all real costs, minimizing total matching cost strictly minimizes the
 * number of infeasible
 * pairs first before minimizing real costs.</li>
 * <li>Padding the cost matrix with zero-cost dummy rows or columns expands the
 * rectangular problem
 * into an N x N square matrix. This preserves feasibility, allows the Hungarian
 * algorithm to find
 * a minimum-weight perfect matching in O(N^3) time, and ensures unassigned
 * slots or surplus volunteers
 * are matched to dummy nodes at zero cost without distorting real
 * assignments.</li>
 * <li>SlotNumber values of assignments may have gaps (e.g. slots 1 and 3
 * filled, 2 unfilled).</li>
 * </ul>
 */
public final class AssignmentSolver {

    public static final int MAX_SIZE = 500;
    public static final long BIG_M = 1_000_000_000_000L;

    private AssignmentSolver() {
    }

    /**
     * Computes the globally optimal assignment of volunteers to task slots.
     * <p>
     * Note that slotNumber values of assignments may have gaps (e.g. slots 1 and 3
     * filled, 2 unfilled).
     *
     * @param volunteers candidate volunteers, non-null
     * @param tasks      task requirements, non-null
     * @param config     scoring weights and bonus configuration, non-null
     * @return deterministic assignment result with assignments, unfilled tasks, and
     *         total cost
     * @throws IllegalArgumentException on null arguments, null elements, duplicate
     *                                  ids, or size &gt; 500
     */
    public static SolverResult solve(
            List<SolverVolunteer> volunteers,
            List<SolverTask> tasks,
            ScoringConfig config) {
        SolverSupport.validateInput(volunteers, tasks, config);

        List<SolverVolunteer> sortedVolunteers = SolverSupport.sortVolunteers(volunteers);
        List<SolverTask> sortedTasks = SolverSupport.sortTasks(tasks);
        List<SolverSupport.TaskSlot> slots = SolverSupport.expandSlots(sortedTasks);

        // Empty volunteers or tasks: return empty assignments and unfilled tasks
        if (sortedTasks.isEmpty()) {
            return new SolverResult(List.of(), List.of(), 0L);
        }
        if (sortedVolunteers.isEmpty()) {
            List<UnfilledTask> unfilled = SolverSupport.buildUnfilled(sortedTasks, Map.of(), volunteers);
            return new SolverResult(List.of(), unfilled, 0L);
        }

        int vCount = sortedVolunteers.size();
        int sCount = slots.size();
        int n = Math.max(vCount, sCount);

        long[][] cost = new long[n][n];

        for (int i = 0; i < vCount; i++) {
            SolverVolunteer v = sortedVolunteers.get(i);
            for (int j = 0; j < sCount; j++) {
                SolverSupport.TaskSlot ts = slots.get(j);
                FitResult fit = ScoringService.score(v, ts.task(), config);
                if (fit.feasible()) {
                    cost[i][j] = SolverSupport.cost(fit);
                } else {
                    cost[i][j] = BIG_M;
                }
            }
        }

        int[] rowToCol = HungarianAlgorithm.solve(cost);

        int[] colToRow = new int[n];
        for (int i = 0; i < n; i++) {
            colToRow[rowToCol[i]] = i;
        }

        List<AssignedPair> assignments = new ArrayList<>();
        Map<UUID, Integer> taskFilledCount = new HashMap<>();
        long totalCost = 0L;

        // Iterate over slots in canonical order (task date, start time, task id, slot
        // number)
        for (int j = 0; j < sCount; j++) {
            int i = colToRow[j];
            if (i < vCount && cost[i][j] != BIG_M) {
                SolverSupport.TaskSlot ts = slots.get(j);
                SolverVolunteer v = sortedVolunteers.get(i);
                FitResult fit = ScoringService.score(v, ts.task(), config);
                assignments.add(new AssignedPair(v.id(), ts.task().id(), ts.slotNumber(), fit));
                taskFilledCount.put(ts.task().id(), taskFilledCount.getOrDefault(ts.task().id(), 0) + 1);
                totalCost += cost[i][j];
            }
        }

        List<UnfilledTask> unfilled = SolverSupport.buildUnfilled(sortedTasks, taskFilledCount, volunteers);
        return new SolverResult(assignments, unfilled, totalCost);
    }

    /**
     * Determines the rarest mandatory skill for an unfilled task among input
     * volunteers.
     * Delegates to SolverSupport.
     *
     * @param task       task being evaluated
     * @param volunteers list of all input volunteers
     * @return rarest mandatory skill name using first-seen spelling, or null if all
     *         held or none required
     */
    static String computeMissingSkill(SolverTask task, List<SolverVolunteer> volunteers) {
        return SolverSupport.computeMissingSkill(task, volunteers);
    }
}
