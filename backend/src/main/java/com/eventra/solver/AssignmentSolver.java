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
 * <li>The solver optimizes a three-level hierarchical objective in lexicographic order:
 * <ol>
 *   <li>Fewest infeasible pairs (maximum number of feasible assignments)</li>
 *   <li>Most critical slots filled</li>
 *   <li>Minimum total fit cost</li>
 * </ol>
 * </li>
 * <li>This hierarchy is achieved using stratified penalties and bounds on problem size (N &le; 500):
 * <ul>
 *   <li>Maximum real cost sum: 500 &times; 100,000 = 5 &times; 10^7 &lt; {@code CRITICAL_UNFILLED_PENALTY} (10^9 = 1,000,000,000L).</li>
 *   <li>Maximum critical penalty sum: 500 &times; {@code CRITICAL_UNFILLED_PENALTY} = 5 &times; 10^11 &lt; {@code BIG_M} (10^12 = 1,000,000,000,000L).</li>
 * </ul>
 * Because each layer's maximum total penalty is strictly less than a single penalty of the higher layer,
 * minimizing Hungarian matching cost strictly optimizes objective (1) first, objective (2) second, and objective (3) third.</li>
 * <li>Padding the cost matrix with dummy rows (with critical penalties for critical slots when volunteers &lt; slots)
 * or dummy columns (at zero cost when volunteers &gt; slots) expands the rectangular problem into an N x N square matrix.</li>
 * <li>SlotNumber values of assignments may have gaps (e.g. slots 1 and 3 filled, 2 unfilled).</li>
 * </ul>
 */
public final class AssignmentSolver {

    public static final int MAX_SIZE = 500;
    public static final long BIG_M = 1_000_000_000_000L;
    public static final long CRITICAL_UNFILLED_PENALTY = 1_000_000_000L;

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
                    cost[i][j] = BIG_M + (ts.task().critical() ? CRITICAL_UNFILLED_PENALTY : 0L);
                }
            }
        }

        // Dummy volunteer rows (padding when volunteers < slots)
        for (int i = vCount; i < n; i++) {
            for (int j = 0; j < sCount; j++) {
                SolverSupport.TaskSlot ts = slots.get(j);
                if (ts.task().critical()) {
                    cost[i][j] = CRITICAL_UNFILLED_PENALTY;
                } else {
                    cost[i][j] = 0L;
                }
            }
            // dummy slot columns (j >= sCount) remain 0L by default
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
            if (i < vCount && cost[i][j] < BIG_M) {
                SolverSupport.TaskSlot ts = slots.get(j);
                SolverVolunteer v = sortedVolunteers.get(i);
                FitResult fit = ScoringService.score(v, ts.task(), config);
                assignments.add(new AssignedPair(v.id(), ts.task().id(), ts.slotNumber(), fit));
                taskFilledCount.put(ts.task().id(), taskFilledCount.getOrDefault(ts.task().id(), 0) + 1);
                totalCost += SolverSupport.cost(fit);
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
