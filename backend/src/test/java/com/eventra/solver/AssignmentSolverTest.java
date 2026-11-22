package com.eventra.solver;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.ScoringService;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import com.eventra.solver.model.AssignedPair;
import com.eventra.solver.model.SolverResult;
import com.eventra.solver.model.UnfilledTask;
import com.eventra.validation.PlanValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests, cross-checks, determinism, greedy comparison, edge cases, and property tests
 * for AssignmentSolver and GreedyBaseline.
 */
class AssignmentSolverTest {

    private static final ScoreWeights DEFAULT_WEIGHTS = new ScoreWeights(45, 15, 15, 15, 10);
    private static final ScoringConfig DEFAULT_CONFIG = new ScoringConfig(DEFAULT_WEIGHTS, 10, 8);

    private static final String[] POSSIBLE_SKILLS = {"Java", "Python", "Docker", "Audio", "Lighting"};

    // -------------------------------------------------------------------------
    // TEST (b): Brute Force Cross-Check (seed 7, 300 cases)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("b) Brute force cross-check: 300 cases (seed 7) match optimal (assignment count, totalCost)")
    void bruteForceCrossCheck_300Cases() {
        Random rng = new Random(7);
        int casesWithAtLeast2Assignments = 0;
        int casesWithAtLeast1Unfilled = 0;

        for (int c = 0; c < 300; c++) {
            int numVolunteers = 1 + rng.nextInt(6); // 1..6
            List<SolverVolunteer> volunteers = new ArrayList<>();
            for (int v = 0; v < numVolunteers; v++) {
                volunteers.add(generateRandomVolunteer(rng, "Vol-" + c + "-" + v, 6, 14));
            }

            // Generate tasks with total slots <= 6
            int remainingSlots = 1 + rng.nextInt(6);
            List<SolverTask> tasks = new ArrayList<>();
            int taskIdx = 0;
            while (remainingSlots > 0) {
                int headcount = Math.min(remainingSlots, 1 + rng.nextInt(3)); // 1..3
                remainingSlots -= headcount;
                tasks.add(generateRandomTask(rng, "Task-" + c + "-" + taskIdx++, headcount));
            }

            SolverResult solverResult = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
            BruteForceResult bfResult = runBruteForce(volunteers, tasks, DEFAULT_CONFIG);

            assertEquals(bfResult.count, solverResult.assignments().size(),
                    "Case " + c + ": Assignment count mismatch between solver and brute force");
            assertEquals(bfResult.totalCost, solverResult.totalCost(),
                    "Case " + c + ": Total cost mismatch between solver and brute force");

            if (solverResult.assignments().size() >= 2) {
                casesWithAtLeast2Assignments++;
            }
            if (!solverResult.unfilled().isEmpty()) {
                casesWithAtLeast1Unfilled++;
            }
        }

        System.out.println("Cases with at least 2 assignments: " + casesWithAtLeast2Assignments);
        System.out.println("Cases with at least 1 unfilled slot: " + casesWithAtLeast1Unfilled);

        assertTrue(casesWithAtLeast2Assignments >= 60,
                "Expected at least 60 cases with >= 2 assignments, got: " + casesWithAtLeast2Assignments);
        assertTrue(casesWithAtLeast1Unfilled >= 60,
                "Expected at least 60 cases with >= 1 unfilled slot, got: " + casesWithAtLeast1Unfilled);
    }

    // -------------------------------------------------------------------------
    // TEST (c): Determinism (20 identical runs and shuffled runs with seed 11)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("c) Determinism: 20 identical runs and shuffled inputs produce identical SolverResult")
    void determinism_identicalAndShuffled() {
        Random setupRng = new Random(99);
        List<SolverVolunteer> volunteers = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            volunteers.add(generateRandomVolunteer(setupRng, "Vol-Det-" + i));
        }
        List<SolverTask> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(generateRandomTask(setupRng, "Task-Det-" + i, 2));
        }

        SolverResult baseline = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);

        // 20 identical runs
        for (int r = 0; r < 20; r++) {
            SolverResult run = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
            assertEquals(baseline, run, "Identical run " + r + " produced different result");
        }

        // Shuffled runs with seed 11
        Random shuffleRng = new Random(11);
        for (int s = 0; s < 20; s++) {
            List<SolverVolunteer> shuffledVolunteers = new ArrayList<>(volunteers);
            Collections.shuffle(shuffledVolunteers, shuffleRng);

            List<SolverTask> shuffledTasks = new ArrayList<>(tasks);
            Collections.shuffle(shuffledTasks, shuffleRng);

            SolverResult shuffledRun = AssignmentSolver.solve(shuffledVolunteers, shuffledTasks, DEFAULT_CONFIG);
            assertEquals(baseline, shuffledRun, "Shuffled run " + s + " produced different result");
        }
    }

    @Test
    @DisplayName("c) Determinism: tie-heavy case with 6 identical volunteers and 3 tasks of headcount 2")
    void determinism_tieHeavyCase() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        List<SolverVolunteer> volunteers = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            volunteers.add(new SolverVolunteer(
                    UUID.randomUUID(), "TieVol-" + i,
                    List.of(new SkillLevel("Java", "Advanced")),
                    3, 4.0,
                    List.of(new AvailabilitySlot("Monday", "Morning")),
                    8, List.of()
            ));
        }

        List<SolverTask> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tasks.add(new SolverTask(
                    UUID.randomUUID(), "TieTask-" + i, monday,
                    LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                    List.of("Java"), List.of(), false
            ));
        }

        SolverResult baseline = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
        assertEquals(6, baseline.assignments().size());
        assertTrue(baseline.unfilled().isEmpty());

        Random shuffleRng = new Random(11);
        for (int s = 0; s < 20; s++) {
            List<SolverVolunteer> shuffledVolunteers = new ArrayList<>(volunteers);
            Collections.shuffle(shuffledVolunteers, shuffleRng);

            List<SolverTask> shuffledTasks = new ArrayList<>(tasks);
            Collections.shuffle(shuffledTasks, shuffleRng);

            SolverResult shuffledRun = AssignmentSolver.solve(shuffledVolunteers, shuffledTasks, DEFAULT_CONFIG);
            assertEquals(baseline, shuffledRun, "Tie-heavy shuffled run " + s + " produced different result");
        }
    }

    // -------------------------------------------------------------------------
    // TEST (d): Greedy Comparison (seed 13, 300 cases) + Hand-built Counterexample
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("d) Greedy comparison: solver key (assignments high, cost low) is never worse than greedy over 300 cases")
    void greedyComparison_300Cases() {
        Random rng = new Random(13);
        int strictlyBetterCount = 0;

        for (int c = 0; c < 300; c++) {
            int numVolunteers = 1 + rng.nextInt(12);
            int numTasks = 1 + rng.nextInt(6);

            List<SolverVolunteer> volunteers = new ArrayList<>();
            for (int v = 0; v < numVolunteers; v++) {
                volunteers.add(generateRandomVolunteer(rng, "Vol-" + c + "-" + v));
            }

            List<SolverTask> tasks = new ArrayList<>();
            for (int t = 0; t < numTasks; t++) {
                tasks.add(generateRandomTask(rng, "Task-" + c + "-" + t, 1 + rng.nextInt(2)));
            }

            SolverResult solverResult = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
            SolverResult greedyResult = GreedyBaseline.solve(volunteers, tasks, DEFAULT_CONFIG);

            int solverCount = solverResult.assignments().size();
            int greedyCount = greedyResult.assignments().size();

            if (solverCount > greedyCount) {
                strictlyBetterCount++;
            } else if (solverCount == greedyCount) {
                if (solverResult.totalCost() < greedyResult.totalCost()) {
                    strictlyBetterCount++;
                }
                assertTrue(solverResult.totalCost() <= greedyResult.totalCost(),
                        "Case " + c + ": Solver cost (" + solverResult.totalCost()
                                + ") should be <= greedy cost (" + greedyResult.totalCost() + ")");
            } else {
                fail("Case " + c + ": Solver assigned fewer pairs (" + solverCount
                        + ") than greedy (" + greedyCount + ")");
            }
        }

        System.out.println("Strictly better cases: " + strictlyBetterCount);
        assertTrue(strictlyBetterCount >= 1, "Expected at least 1 strictly better case, got: " + strictlyBetterCount);
    }

    @Test
    @DisplayName("d) Hand-built case where greedy is strictly worse than optimal solver")
    void greedyStrictlyWorse_handBuiltCase() {
        // Task 1: Monday Morning (09:00 - 11:00)
        LocalDate date = LocalDate.of(2026, 11, 16); // Monday
        SolverTask task1 = new SolverTask(
                UUID.randomUUID(), "Task 1 Morning", date,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Java"), List.of(), false
        );

        // Task 2: Monday Afternoon (13:00 - 15:00)
        SolverTask task2 = new SolverTask(
                UUID.randomUUID(), "Task 2 Afternoon", date,
                LocalTime.of(13, 0), LocalTime.of(15, 0), 1,
                List.of("Docker"), List.of(), false
        );

        // Volunteer A: available both Morning and Afternoon; has Java (Advanced) and Docker (Advanced)
        SolverVolunteer volunteerA = new SolverVolunteer(
                UUID.randomUUID(), "Volunteer A",
                List.of(new SkillLevel("Java", "Advanced"), new SkillLevel("Docker", "Advanced")),
                5, 5.0,
                List.of(new AvailabilitySlot("Monday", "Morning"), new AvailabilitySlot("Monday", "Afternoon")),
                8, List.of()
        );

        // Volunteer B: available Morning ONLY; has Java (Beginner), no Docker
        SolverVolunteer volunteerB = new SolverVolunteer(
                UUID.randomUUID(), "Volunteer B",
                List.of(new SkillLevel("Java", "Beginner")),
                1, 3.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );

        List<SolverVolunteer> volunteers = List.of(volunteerA, volunteerB);
        List<SolverTask> tasks = List.of(task1, task2);

        SolverResult greedyResult = GreedyBaseline.solve(volunteers, tasks, DEFAULT_CONFIG);
        assertEquals(1, greedyResult.assignments().size());
        assertEquals(volunteerA.id(), greedyResult.assignments().get(0).volunteerId());
        assertEquals(1, greedyResult.unfilled().size());

        SolverResult solverResult = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
        assertEquals(2, solverResult.assignments().size());
        assertEquals(0, solverResult.unfilled().size());

        AssignedPair p1 = solverResult.assignments().get(0); // Task 1
        AssignedPair p2 = solverResult.assignments().get(1); // Task 2
        assertEquals(task1.id(), p1.taskId());
        assertEquals(volunteerB.id(), p1.volunteerId());
        assertEquals(task2.id(), p2.taskId());
        assertEquals(volunteerA.id(), p2.volunteerId());
    }

    // -------------------------------------------------------------------------
    // TEST (e): Edge Cases & Validation (split into focused methods 1 to 10)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("e1) Edge case: empty tasks returns empty assignments and unfilled")
    void emptyTasks() {
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Solo",
                List.of(new SkillLevel("Audio", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverResult rEmptyTasks = AssignmentSolver.solve(List.of(volunteer), List.of(), DEFAULT_CONFIG);
        assertTrue(rEmptyTasks.assignments().isEmpty());
        assertTrue(rEmptyTasks.unfilled().isEmpty());
        assertEquals(0L, rEmptyTasks.totalCost());
    }

    @Test
    @DisplayName("e2) Edge case: empty volunteers returns all tasks unfilled")
    void emptyVolunteers() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Setup", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of("Audio"), List.of(), false
        );
        SolverResult rEmptyVolunteers = AssignmentSolver.solve(List.of(), List.of(task), DEFAULT_CONFIG);
        assertTrue(rEmptyVolunteers.assignments().isEmpty());
        assertEquals(1, rEmptyVolunteers.unfilled().size());
        assertEquals(2, rEmptyVolunteers.unfilled().get(0).slotsUnfilled());
        assertEquals(0L, rEmptyVolunteers.totalCost());
    }

    @Test
    @DisplayName("e3) Edge case: more slots than volunteers gives exact unfilled count")
    void moreSlotsThanVolunteers() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Setup", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of("Audio"), List.of(), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Solo",
                List.of(new SkillLevel("Audio", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverResult rMoreSlots = AssignmentSolver.solve(List.of(volunteer), List.of(task), DEFAULT_CONFIG);
        assertEquals(1, rMoreSlots.assignments().size());
        assertEquals(1, rMoreSlots.unfilled().size());
        assertEquals(1, rMoreSlots.unfilled().get(0).slotsUnfilled());
    }

    @Test
    @DisplayName("e4) Edge case: overlapping tasks volunteer used at most once")
    void overlappingTasksVolunteerUsedOnce() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Setup", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of("Audio"), List.of(), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Solo",
                List.of(new SkillLevel("Audio", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask taskOverlap = new SolverTask(
                UUID.randomUUID(), "Overlap", monday,
                LocalTime.of(9, 30), LocalTime.of(11, 30), 1,
                List.of("Audio"), List.of(), false
        );
        SolverResult rOverlap = AssignmentSolver.solve(List.of(volunteer), List.of(task, taskOverlap), DEFAULT_CONFIG);
        assertEquals(1, rOverlap.assignments().size());
        assertEquals(volunteer.id(), rOverlap.assignments().get(0).volunteerId());
    }

    @Test
    @DisplayName("e5) Edge case: all pairs infeasible returns zero assignments and everything unfilled")
    void allPairsInfeasible() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Setup", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of("Audio"), List.of(), false
        );
        SolverVolunteer missingSkillVol = new SolverVolunteer(
                UUID.randomUUID(), "WrongSkill",
                List.of(new SkillLevel("Lighting", "Advanced")),
                3, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverResult rAllInfeasible = AssignmentSolver.solve(List.of(missingSkillVol), List.of(task), DEFAULT_CONFIG);
        assertTrue(rAllInfeasible.assignments().isEmpty());
        assertEquals(1, rAllInfeasible.unfilled().size());
        assertEquals(2, rAllInfeasible.unfilled().get(0).slotsUnfilled());
        assertEquals(0L, rAllInfeasible.totalCost());
        assertEquals("Audio", rAllInfeasible.unfilled().get(0).missingSkill());
    }

    @Test
    @DisplayName("e6) Edge case: missingSkill rarest, tie-break alphabetical, and null if all held")
    void missingSkillRarestAndTieBreakAndNull() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask multiSkillTask = new SolverTask(
                UUID.randomUUID(), "Multi", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Docker", "Rust", "Audio"), List.of(), false
        );
        SolverVolunteer vRustAudio = new SolverVolunteer(
                UUID.randomUUID(), "V1",
                List.of(new SkillLevel("Rust", "Beginner"), new SkillLevel("Audio", "Beginner")),
                1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        assertEquals("Docker", AssignmentSolver.computeMissingSkill(multiSkillTask, List.of(vRustAudio)));

        // Ties in rarest skill: [Rust, Docker] both held by 0 volunteers -> Docker before Rust alphabetically
        SolverTask tieTask = new SolverTask(
                UUID.randomUUID(), "Tie", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Rust", "Docker"), List.of(), false
        );
        assertEquals("Docker", AssignmentSolver.computeMissingSkill(tieTask, List.of()));

        // All mandatory skills held by at least 1 volunteer -> null
        SolverVolunteer vDocker = new SolverVolunteer(
                UUID.randomUUID(), "V2",
                List.of(new SkillLevel("Docker", "Beginner")),
                1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        assertEquals(null, AssignmentSolver.computeMissingSkill(multiSkillTask, List.of(vRustAudio, vDocker)));
    }

    @Test
    @DisplayName("e7) Validation: null arguments and duplicate ids throw IllegalArgumentException")
    void nullArgumentsAndDuplicateIdsThrow() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(null, List.of(), DEFAULT_CONFIG));
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(List.of(), null, DEFAULT_CONFIG));
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(List.of(), List.of(), null));

        UUID dupId = UUID.randomUUID();
        SolverVolunteer vDup1 = new SolverVolunteer(dupId, "V1", List.of(), 0, null, List.of(), 8, List.of());
        SolverVolunteer vDup2 = new SolverVolunteer(dupId, "V2", List.of(), 0, null, List.of(), 8, List.of());
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(List.of(vDup1, vDup2), List.of(), DEFAULT_CONFIG));

        SolverTask tDup1 = new SolverTask(dupId, "T1", monday, LocalTime.of(9, 0), LocalTime.of(10, 0), 1, List.of(), List.of(), false);
        SolverTask tDup2 = new SolverTask(dupId, "T2", monday, LocalTime.of(9, 0), LocalTime.of(10, 0), 1, List.of(), List.of(), false);
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(List.of(), List.of(tDup1, tDup2), DEFAULT_CONFIG));
    }

    @Test
    @DisplayName("e8) Validation: null volunteer element and null task element throw IllegalArgumentException")
    void nullElementsThrow() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Setup", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of("Audio"), List.of(), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Solo",
                List.of(new SkillLevel("Audio", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );

        List<SolverVolunteer> volWithNull = new ArrayList<>();
        volWithNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(volWithNull, List.of(task), DEFAULT_CONFIG));

        List<SolverTask> taskWithNull = new ArrayList<>();
        taskWithNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> AssignmentSolver.solve(List.of(volunteer), taskWithNull, DEFAULT_CONFIG));
    }

    @Test
    @DisplayName("e9) Validation: over 500 volunteers throws IllegalArgumentException")
    void over500Throws() {
        List<SolverVolunteer> vol501 = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            vol501.add(new SolverVolunteer(UUID.randomUUID(), "V" + i, List.of(), 0, null, List.of(), 8, List.of()));
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AssignmentSolver.solve(vol501, List.of(), DEFAULT_CONFIG));
        assertEquals("max(volunteers, slots) exceeds 500", ex.getMessage());
    }

    @Test
    @DisplayName("e10) Scale: 150 volunteers x 150 slots solve completes and passes PlanValidator")
    void large150x150PassesValidator() {
        Random largeRng = new Random(123);
        List<SolverVolunteer> largeVolunteers = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            largeVolunteers.add(generateRandomVolunteer(largeRng, "LargeVol-" + i));
        }
        List<SolverTask> largeTasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            largeTasks.add(generateRandomTask(largeRng, "LargeTask-" + i, 3));
        }
        SolverResult largeResult = AssignmentSolver.solve(largeVolunteers, largeTasks, DEFAULT_CONFIG);
        assertNotNull(largeResult);
        List<String> largeViolations = PlanValidator.validate(largeVolunteers, largeTasks, DEFAULT_CONFIG, largeResult);
        assertTrue(largeViolations.isEmpty(), "PlanValidator reported violations on 150x150 solve: " + largeViolations);
    }

    // -------------------------------------------------------------------------
    // TEST (g): Property Test (200 seeded cases, feasible fits, [0, 100])
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("g) Property: for 200 seeded cases, no AssignedPair has infeasible fit and fits are within [0, 100]")
    void propertyTest_200Cases() {
        Random rng = new Random(42);

        for (int c = 0; c < 200; c++) {
            int numVolunteers = 1 + rng.nextInt(10);
            int numTasks = 1 + rng.nextInt(5);

            List<SolverVolunteer> volunteers = new ArrayList<>();
            for (int v = 0; v < numVolunteers; v++) {
                volunteers.add(generateRandomVolunteer(rng, "PropVol-" + c + "-" + v));
            }

            List<SolverTask> tasks = new ArrayList<>();
            for (int t = 0; t < numTasks; t++) {
                tasks.add(generateRandomTask(rng, "PropTask-" + c + "-" + t, 1 + rng.nextInt(3)));
            }

            SolverResult result = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
            assertNotNull(result);

            for (AssignedPair pair : result.assignments()) {
                assertNotNull(pair.fit(), "FitResult cannot be null");
                assertTrue(pair.fit().feasible(), "Assigned pair must be feasible");
                assertTrue(pair.fit().fit() >= 0.0 && pair.fit().fit() <= 100.0,
                        "Fit score must be within [0.0, 100.0], got: " + pair.fit().fit());
            }

            // Invariant: totalCost equals sum of real assignment costs
            long expectedCost = 0L;
            for (AssignedPair pair : result.assignments()) {
                expectedCost += Math.round((100.0 - pair.fit().fit()) * 1000.0);
            }
            assertEquals(expectedCost, result.totalCost(), "totalCost invariant check");
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods & Independent Brute Force
    // -------------------------------------------------------------------------
    private record BruteForceResult(int count, long totalCost) {
    }

    private static BruteForceResult runBruteForce(
            List<SolverVolunteer> volunteers,
            List<SolverTask> tasks,
            ScoringConfig config
    ) {
        List<SolverVolunteer> sortedVolunteers = new ArrayList<>(volunteers);
        sortedVolunteers.sort(Comparator.comparing(SolverVolunteer::id));

        List<SolverTask> sortedTasks = new ArrayList<>(tasks);
        sortedTasks.sort(Comparator.comparing(SolverTask::date)
                .thenComparing(SolverTask::startTime)
                .thenComparing(SolverTask::id));

        record SlotRef(SolverTask task, int slotNumber) {}
        List<SlotRef> slots = new ArrayList<>();
        for (SolverTask t : sortedTasks) {
            for (int s = 1; s <= t.headcount(); s++) {
                slots.add(new SlotRef(t, s));
            }
        }

        int V = sortedVolunteers.size();
        int S = slots.size();
        if (V == 0 || S == 0) {
            return new BruteForceResult(0, 0L);
        }

        boolean[][] feasible = new boolean[V][S];
        long[][] realCost = new long[V][S];
        for (int i = 0; i < V; i++) {
            SolverVolunteer v = sortedVolunteers.get(i);
            for (int j = 0; j < S; j++) {
                SlotRef sr = slots.get(j);
                FitResult fit = ScoringService.score(v, sr.task(), config);
                feasible[i][j] = fit.feasible();
                if (fit.feasible()) {
                    realCost[i][j] = Math.round((100.0 - fit.fit()) * 1000.0);
                }
            }
        }

        int[] bestCount = new int[]{-1};
        long[] bestCost = new long[]{Long.MAX_VALUE};
        boolean[] usedSlots = new boolean[S];

        searchBruteForce(0, V, S, feasible, realCost, 0, 0L, usedSlots, bestCount, bestCost);
        return new BruteForceResult(bestCount[0], bestCount[0] > 0 ? bestCost[0] : 0L);
    }

    private static void searchBruteForce(
            int v, int V, int S,
            boolean[][] feasible, long[][] realCost,
            int curCount, long curCost,
            boolean[] usedSlots,
            int[] bestCount, long[] bestCost
    ) {
        if (v == V) {
            if (curCount > bestCount[0]) {
                bestCount[0] = curCount;
                bestCost[0] = curCost;
            } else if (curCount == bestCount[0] && curCost < bestCost[0]) {
                bestCost[0] = curCost;
            }
            return;
        }

        if (curCount + (V - v) < bestCount[0]) {
            return;
        }

        // Branch 1: assign volunteer v to an available feasible slot
        for (int s = 0; s < S; s++) {
            if (!usedSlots[s] && feasible[v][s]) {
                usedSlots[s] = true;
                searchBruteForce(v + 1, V, S, feasible, realCost, curCount + 1, curCost + realCost[v][s], usedSlots, bestCount, bestCost);
                usedSlots[s] = false;
            }
        }

        // Branch 2: do not assign volunteer v
        searchBruteForce(v + 1, V, S, feasible, realCost, curCount, curCost, usedSlots, bestCount, bestCost);
    }

    private static SolverVolunteer generateRandomVolunteer(Random rng, String name, int minSlots, int maxSlots) {
        int numSkills = 1 + rng.nextInt(3);
        List<SkillLevel> skills = new ArrayList<>();
        for (int s = 0; s < numSkills; s++) {
            String skill = POSSIBLE_SKILLS[rng.nextInt(POSSIBLE_SKILLS.length)];
            String level = ModelValues.SKILL_LEVELS.get(rng.nextInt(ModelValues.SKILL_LEVELS.size()));
            skills.add(new SkillLevel(skill, level));
        }

        int pastEvents = rng.nextInt(10);
        Double rating = rng.nextBoolean() ? null : (1.0 + 4.0 * rng.nextDouble());

        int numSlots = minSlots + rng.nextInt(maxSlots - minSlots + 1);
        List<AvailabilitySlot> avail = new ArrayList<>();
        for (int a = 0; a < numSlots; a++) {
            String day = ModelValues.DAYS.get(rng.nextInt(ModelValues.DAYS.size()));
            String slot = ModelValues.SLOTS.get(rng.nextInt(ModelValues.SLOTS.size()));
            avail.add(new AvailabilitySlot(day, slot));
        }

        int maxHours = (rng.nextDouble() < 0.15) ? 0 : (2 + rng.nextInt(8));
        int numInterests = rng.nextInt(2);
        List<String> interests = new ArrayList<>();
        for (int in = 0; in < numInterests; in++) {
            interests.add(POSSIBLE_SKILLS[rng.nextInt(POSSIBLE_SKILLS.length)]);
        }

        return new SolverVolunteer(UUID.randomUUID(), name, skills, pastEvents, rating, avail, maxHours, interests);
    }

    private static SolverVolunteer generateRandomVolunteer(Random rng, String name) {
        return generateRandomVolunteer(rng, name, 1, 6);
    }

    private static SolverTask generateRandomTask(Random rng, String title, int headcount) {
        LocalDate date = LocalDate.of(2026, 11, 16).plusDays(rng.nextInt(7));
        int startHour = rng.nextInt(24);
        LocalTime startTime = LocalTime.of(startHour, 0);
        int durationHours = 1 + rng.nextInt(4);
        LocalTime endTime = startTime.plusHours(durationHours);

        int numMandatory = rng.nextInt(2);
        List<String> mandatory = new ArrayList<>();
        for (int m = 0; m < numMandatory; m++) {
            mandatory.add(POSSIBLE_SKILLS[rng.nextInt(POSSIBLE_SKILLS.length)]);
        }

        int numPreferred = rng.nextInt(2);
        List<String> preferred = new ArrayList<>();
        for (int p = 0; p < numPreferred; p++) {
            preferred.add(POSSIBLE_SKILLS[rng.nextInt(POSSIBLE_SKILLS.length)]);
        }

        boolean critical = rng.nextBoolean();
        return new SolverTask(UUID.randomUUID(), title, date, startTime, endTime, headcount, mandatory, preferred, critical);
    }
}
