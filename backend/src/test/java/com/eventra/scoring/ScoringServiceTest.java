package com.eventra.scoring;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.model.FitBreakdown;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain JUnit 5 unit tests for deterministic ScoringService.
 * Contains no Spring context, no database access, and no network I/O.
 */
class ScoringServiceTest {

    private static final ScoreWeights DEFAULT_WEIGHTS = new ScoreWeights(45, 15, 15, 15, 10);
    private static final ScoringConfig DEFAULT_CONFIG = new ScoringConfig(DEFAULT_WEIGHTS, 10, 8);

    @Test
    @DisplayName("Golden Case 1: perfect volunteer, no-skill task, default weights 45/15/15/15/10 -> exact expected fit")
    void goldenCase1_perfectVolunteer_noSkillTask() {
        // Date: 2026-11-16 is a Monday
        LocalDate date = LocalDate.of(2026, 11, 16);
        LocalTime startTime = LocalTime.of(9, 0); // Morning (05:00 - 11:59)
        LocalTime endTime = LocalTime.of(11, 0); // 2 hours duration

        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(),
                "Alice Perfect",
                List.of(new SkillLevel("Sound", "expert")),
                5, // pastEventsCount: min(5 / 5.0, 1.0) = 1.0
                5.0, // averageRating: 5.0 / 5.0 = 1.0
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, // maxHours: 8 >= 2.0
                List.of("Logistics") // matches title word "Logistics" -> interest = 1.0
        );

        SolverTask task = new SolverTask(
                UUID.randomUUID(),
                "Festival Logistics Setup",
                date,
                startTime,
                endTime,
                2,
                List.of(), // no mandatory skills
                List.of(), // no preferred skills
                false // not critical
        );

        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);

        assertTrue(result.feasible());
        assertNull(result.infeasibleReason());

        // Arithmetic hand calculation:
        // - Task has no skills at all -> skillFactor = 1.0
        // - pastEventsCount = 5 -> experienceFactor = min(5 / 5.0, 1.0) = 1.0
        // - averageRating = 5.0 -> performanceFactor = 5.0 / 5.0 = 1.0
        // - loadFactor = 1.0 (single-task model)
        // - interest "Logistics" matches title word "Logistics" -> interestFactor = 1.0
        // Weights: wS=45, wE=15, wP=15, wL=15, wI=10 (sum = 100)
        // fit = (45 * 1.0) + (15 * 1.0) + (15 * 1.0) + (15 * 1.0) + (10 * 1.0)
        //     = 45.0 + 15.0 + 15.0 + 15.0 + 10.0 = 100.0
        // pastEventsCount = 5 != 0 -> firstTimerBonus = 0
        // Total expected fit = 100.0
        assertEquals(100.0, result.fit(), 1e-9);

        assertNotNull(result.breakdown());
        assertEquals(1.0, result.breakdown().skill(), 1e-9);
        assertEquals(1.0, result.breakdown().experience(), 1e-9);
        assertEquals(1.0, result.breakdown().performance(), 1e-9);
        assertEquals(1.0, result.breakdown().load(), 1e-9);
        assertEquals(1.0, result.breakdown().interest(), 1e-9);
        assertTrue(result.flags().isEmpty());
    }

    @Test
    @DisplayName("Golden Case 2: partial skills, null rating, pastEvents 2 -> exact expected fit")
    void goldenCase2_partialSkills_nullRating_pastEvents2() {
        // Date: 2026-11-16 is a Monday
        LocalDate date = LocalDate.of(2026, 11, 16);
        LocalTime startTime = LocalTime.of(9, 0); // Morning
        LocalTime endTime = LocalTime.of(11, 0); // 2 hours duration

        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(),
                "Bob Partial",
                List.of(new SkillLevel("Java", "intermediate")), // intermediate = 0.50
                2, // pastEventsCount: min(2 / 5.0, 1.0) = 0.4
                null, // null rating -> performance = 0.5
                List.of(new AvailabilitySlot("Monday", "Morning")),
                5, // maxHours: 5 >= 2.0
                List.of("Music") // does not match task skills or title words -> interest = 0.0
        );

        SolverTask task = new SolverTask(
                UUID.randomUUID(),
                "Backend Architecture Workshop",
                date,
                startTime,
                endTime,
                1,
                List.of("Java"), // mandatory
                List.of("Docker"), // preferred
                false // not critical
        );

        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);

        assertTrue(result.feasible());
        assertNull(result.infeasibleReason());

        // Arithmetic hand calculation:
        // - Task skills: mandatory ["Java"], preferred ["Docker"] -> 2 skills total
        //   Volunteer has "Java" at intermediate (0.50) and lacks "Docker" (0.0)
        //   skillFactor = (0.50 + 0.0) / 2 = 0.25
        // - pastEventsCount = 2 -> experienceFactor = 2 / 5.0 = 0.4
        // - averageRating = null -> performanceFactor = 0.5
        // - loadFactor = 1.0
        // - interest "Music" matches neither skills ("Java", "Docker") nor title words ("Backend", "Architecture", "Workshop")
        //   -> interestFactor = 0.0
        // Base weights: wS=45, wE=15, wP=15, wL=15, wI=10
        // fit = (45 * 0.25) + (15 * 0.4) + (15 * 0.5) + (15 * 1.0) + (10 * 0.0)
        //     = 11.25 + 6.0 + 7.5 + 15.0 + 0.0
        //     = 39.75
        // pastEventsCount = 2 != 0 -> firstTimerBonus = 0
        // Total expected fit = 39.75
        assertEquals(39.75, result.fit(), 1e-9);

        assertNotNull(result.breakdown());
        assertEquals(0.25, result.breakdown().skill(), 1e-9);
        assertEquals(0.4, result.breakdown().experience(), 1e-9);
        assertEquals(0.5, result.breakdown().performance(), 1e-9);
        assertEquals(1.0, result.breakdown().load(), 1e-9);
        assertEquals(0.0, result.breakdown().interest(), 1e-9);
        assertTrue(result.flags().isEmpty());
    }

    @Test
    @DisplayName("Missing mandatory skill -> infeasible, reason names the skill, fit 0")
    void missingMandatorySkill_infeasible() {
        LocalDate date = LocalDate.of(2026, 11, 16);
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(),
                "Charlie",
                List.of(new SkillLevel("Lighting", "advanced")),
                3,
                4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                6,
                List.of()
        );

        SolverTask task = new SolverTask(
                UUID.randomUUID(),
                "Stage Audio Operations",
                date,
                LocalTime.of(8, 0),
                LocalTime.of(10, 0),
                1,
                List.of("Audio"), // missing mandatory skill
                List.of("Lighting"),
                false
        );

        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);

        assertFalse(result.feasible());
        // Arithmetic hand calculation: Infeasible fit is strictly 0.0
        assertEquals(0.0, result.fit(), 1e-9);
        assertNotNull(result.infeasibleReason());
        assertTrue(result.infeasibleReason().toLowerCase().contains("audio"));
        assertNull(result.breakdown());
    }

    @Test
    @DisplayName("Availability: boundary times 11:59 vs 12:00, 16:59 vs 17:00, 00:30 start as Evening, Sunday vs Monday")
    void availabilityBoundaries_andWeekday() {
        // Fixed dates:
        // 2026-11-15 is a Sunday
        LocalDate sunday = LocalDate.of(2026, 11, 15);
        // 2026-11-16 is a Monday
        LocalDate monday = LocalDate.of(2026, 11, 16);

        // 1. Boundary 11:59 (Morning) vs 12:00 (Afternoon)
        SolverVolunteer morningOnlyVolunteer = new SolverVolunteer(
                UUID.randomUUID(), "Morning Volunteer", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 8, List.of()
        );
        SolverTask morningBoundaryTask = new SolverTask(
                UUID.randomUUID(), "Task 11:59", monday,
                LocalTime.of(11, 59), LocalTime.of(13, 0), 1, List.of(), List.of(), false
        );
        SolverTask afternoonBoundaryTask = new SolverTask(
                UUID.randomUUID(), "Task 12:00", monday,
                LocalTime.of(12, 0), LocalTime.of(14, 0), 1, List.of(), List.of(), false
        );

        assertTrue(ScoringService.score(morningOnlyVolunteer, morningBoundaryTask, DEFAULT_CONFIG).feasible());
        FitResult rAfternoon = ScoringService.score(morningOnlyVolunteer, afternoonBoundaryTask, DEFAULT_CONFIG);
        assertFalse(rAfternoon.feasible());
        assertTrue(rAfternoon.infeasibleReason().contains("Not available"));

        // 2. Boundary 16:59 (Afternoon) vs 17:00 (Evening)
        SolverVolunteer afternoonOnlyVolunteer = new SolverVolunteer(
                UUID.randomUUID(), "Afternoon Volunteer", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Afternoon")), 8, List.of()
        );
        SolverTask afternoonBoundaryTask2 = new SolverTask(
                UUID.randomUUID(), "Task 16:59", monday,
                LocalTime.of(16, 59), LocalTime.of(18, 0), 1, List.of(), List.of(), false
        );
        SolverTask eveningBoundaryTask = new SolverTask(
                UUID.randomUUID(), "Task 17:00", monday,
                LocalTime.of(17, 0), LocalTime.of(19, 0), 1, List.of(), List.of(), false
        );

        assertTrue(ScoringService.score(afternoonOnlyVolunteer, afternoonBoundaryTask2, DEFAULT_CONFIG).feasible());
        FitResult rEvening = ScoringService.score(afternoonOnlyVolunteer, eveningBoundaryTask, DEFAULT_CONFIG);
        assertFalse(rEvening.feasible());
        assertTrue(rEvening.infeasibleReason().contains("Not available"));

        // 3. 00:30 start counted as Evening
        SolverVolunteer eveningVolunteer = new SolverVolunteer(
                UUID.randomUUID(), "Evening Volunteer", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Evening")), 8, List.of()
        );
        SolverTask midnightTask = new SolverTask(
                UUID.randomUUID(), "Midnight Task 00:30", monday,
                LocalTime.of(0, 30), LocalTime.of(2, 30), 1, List.of(), List.of(), false
        );
        assertTrue(ScoringService.score(eveningVolunteer, midnightTask, DEFAULT_CONFIG).feasible());

        // 4. Sunday vs Monday (Fixed dates)
        SolverVolunteer sundayVolunteer = new SolverVolunteer(
                UUID.randomUUID(), "Sunday Volunteer", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Sunday", "Morning")), 8, List.of()
        );
        SolverTask sundayTask = new SolverTask(
                UUID.randomUUID(), "Sunday Task", sunday, // 2026-11-15 is Sunday
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false
        );
        SolverTask mondayTask = new SolverTask(
                UUID.randomUUID(), "Monday Task", monday, // 2026-11-16 is Monday
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false
        );
        assertTrue(ScoringService.score(sundayVolunteer, sundayTask, DEFAULT_CONFIG).feasible());
        FitResult rMondayFail = ScoringService.score(sundayVolunteer, mondayTask, DEFAULT_CONFIG);
        assertFalse(rMondayFail.feasible());
        assertTrue(rMondayFail.infeasibleReason().contains("Not available"));
    }

    @Test
    @DisplayName("Over hours: duration exactly equal to maxHours is feasible; one minute more is infeasible")
    void overHours_boundary() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        // maxHours = 4 hours (240 minutes)
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "David", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 4, List.of()
        );

        // 4 hours exactly: 08:00 to 12:00 -> 240 / 60.0 = 4.0 hours <= 4 -> feasible
        SolverTask exactTask = new SolverTask(
                UUID.randomUUID(), "Exact 4h Task", monday,
                LocalTime.of(8, 0), LocalTime.of(12, 0), 1, List.of(), List.of(), false
        );
        assertTrue(ScoringService.score(volunteer, exactTask, DEFAULT_CONFIG).feasible());

        // 4 hours and 1 minute: 08:00 to 12:01 -> 241 / 60.0 = 4.016666667 hours > 4 -> infeasible
        SolverTask oneMinuteMoreTask = new SolverTask(
                UUID.randomUUID(), "4h 1m Task", monday,
                LocalTime.of(8, 0), LocalTime.of(12, 1), 1, List.of(), List.of(), false
        );
        FitResult resultOverHours = ScoringService.score(volunteer, oneMinuteMoreTask, DEFAULT_CONFIG);
        assertFalse(resultOverHours.feasible());
        // Arithmetic hand calculation: Infeasible fit is strictly 0.0
        assertEquals(0.0, resultOverHours.fit(), 1e-9);
        assertTrue(resultOverHours.infeasibleReason().contains("Over hours"));

        // Fallback to defaultMaxHours when volunteer maxHours <= 0
        SolverVolunteer zeroHoursVolunteer = new SolverVolunteer(
                UUID.randomUUID(), "Zero Max Hours", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 0, List.of()
        );
        // defaultMaxHours = 8. Task of 8 hours is feasible, 8h 1m is infeasible
        SolverTask task8h = new SolverTask(
                UUID.randomUUID(), "8h Task", monday,
                LocalTime.of(8, 0), LocalTime.of(16, 0), 1, List.of(), List.of(), false
        );
        SolverTask task8h1m = new SolverTask(
                UUID.randomUUID(), "8h 1m Task", monday,
                LocalTime.of(8, 0), LocalTime.of(16, 1), 1, List.of(), List.of(), false
        );
        assertTrue(ScoringService.score(zeroHoursVolunteer, task8h, DEFAULT_CONFIG).feasible());
        assertFalse(ScoringService.score(zeroHoursVolunteer, task8h1m, DEFAULT_CONFIG).feasible());
    }

    @Test
    @DisplayName("Critical task weights rescale to exactly 100 and change the result in the expected direction (hand-computed)")
    void criticalTask_weightsRescale_andDirection() {
        LocalDate monday = LocalDate.of(2026, 11, 16);

        // Volunteer has strong experience and performance, but weak skill
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Emma",
                List.of(new SkillLevel("Audio", "beginner")), // beginner = 0.25
                5, // pastEventsCount = 5 -> experienceFactor = 1.0
                5.0, // averageRating = 5.0 -> performanceFactor = 1.0
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8,
                List.of() // interestFactor = 0.0
        );

        SolverTask nonCriticalTask = new SolverTask(
                UUID.randomUUID(), "Audio Operation", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Audio"), List.of(), false // non-critical
        );

        SolverTask criticalTask = new SolverTask(
                UUID.randomUUID(), "Audio Operation Critical", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Audio"), List.of(), true // critical
        );

        FitResult nonCriticalResult = ScoringService.score(volunteer, nonCriticalTask, DEFAULT_CONFIG);
        FitResult criticalResult = ScoringService.score(volunteer, criticalTask, DEFAULT_CONFIG);

        // Arithmetic hand calculation for base weights (45 / 15 / 15 / 15 / 10):
        // skillFactor = 0.25, expFactor = 1.0, perfFactor = 1.0, loadFactor = 1.0, interestFactor = 0.0
        // nonCritical fit = (45 * 0.25) + (15 * 1.0) + (15 * 1.0) + (15 * 1.0) + (10 * 0.0)
        //                 = 11.25 + 15.0 + 15.0 + 15.0 + 0.0 = 56.25
        assertEquals(56.25, nonCriticalResult.fit(), 1e-9);

        // Arithmetic hand calculation for critical task:
        // Base weights: wS = 45, wE = 15, wP = 15, wL = 15, wI = 10
        // Multiply experience and performance by 1.5:
        // wE' = 15 * 1.5 = 22.5
        // wP' = 15 * 1.5 = 22.5
        // Sum of unnormalized weights = 45 + 22.5 + 22.5 + 15 + 10 = 115.0
        // Rescaled weights (each multiplied by 100 / 115.0):
        // wS_rescaled = (45 / 115.0) * 100.0 = 4500.0 / 115.0 = 900.0 / 23.0 ≈ 39.1304347826
        // wE_rescaled = (22.5 / 115.0) * 100.0 = 2250.0 / 115.0 = 450.0 / 23.0 ≈ 19.5652173913
        // wP_rescaled = (22.5 / 115.0) * 100.0 = 2250.0 / 115.0 = 450.0 / 23.0 ≈ 19.5652173913
        // wL_rescaled = (15 / 115.0) * 100.0 = 1500.0 / 115.0 = 300.0 / 23.0 ≈ 13.0434782608
        // wI_rescaled = (10 / 115.0) * 100.0 = 1000.0 / 115.0 = 200.0 / 23.0 ≈ 8.6956521739
        // Sum check: (900 + 450 + 450 + 300 + 200) / 23.0 = 2300.0 / 23.0 = 100.0 exactly!
        double rescaledSum = (900.0 / 23.0) + (450.0 / 23.0) + (450.0 / 23.0) + (300.0 / 23.0) + (200.0 / 23.0);
        assertEquals(100.0, rescaledSum, 1e-9);

        // Critical fit = (900.0 / 23.0) * 0.25 + (450.0 / 23.0) * 1.0 + (450.0 / 23.0) * 1.0
        //              + (300.0 / 23.0) * 1.0 + (200.0 / 23.0) * 0.0
        //              = (225.0 + 450.0 + 450.0 + 300.0 + 0.0) / 23.0
        //              = 1425.0 / 23.0 ≈ 61.95652173913044
        double expectedCriticalFit = 1425.0 / 23.0;
        assertEquals(expectedCriticalFit, criticalResult.fit(), 1e-9);

        // Verification of expected direction: 61.9565... > 56.25 (increased due to higher exp & perf weighting)
        assertTrue(criticalResult.fit() > nonCriticalResult.fit());
        assertTrue(criticalResult.flags().contains("Critical task"));
    }

    @Test
    @DisplayName("First-timer bonus applies on non-critical only; fit capped at 100")
    void firstTimerBonus_nonCriticalOnly_andCappedAt100() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        ScoringConfig configWithBonus = new ScoringConfig(DEFAULT_WEIGHTS, 15, 8); // bonus = 15

        // First timer volunteer (pastEventsCount == 0)
        SolverVolunteer firstTimer = new SolverVolunteer(
                UUID.randomUUID(), "Frank FirstTimer",
                List.of(new SkillLevel("Java", "intermediate")), // intermediate = 0.50
                0, // pastEventsCount = 0 -> experienceFactor = 0.0
                null, // null rating -> performanceFactor = 0.5
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8,
                List.of() // interestFactor = 0.0
        );

        SolverTask nonCriticalTask = new SolverTask(
                UUID.randomUUID(), "Coding Session", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Java"), List.of(), false // non-critical
        );

        FitResult nonCriticalResult = ScoringService.score(firstTimer, nonCriticalTask, configWithBonus);

        // Arithmetic hand calculation:
        // skillFactor = 0.50, expFactor = 0.0, perfFactor = 0.5, loadFactor = 1.0, interestFactor = 0.0
        // Base fit = (45 * 0.50) + (15 * 0.0) + (15 * 0.5) + (15 * 1.0) + (10 * 0.0)
        //          = 22.5 + 0.0 + 7.5 + 15.0 + 0.0 = 45.0
        // firstTimerBonus = 15 -> fit with bonus = 45.0 + 15.0 = 60.0
        assertEquals(60.0, nonCriticalResult.fit(), 1e-9);
        assertTrue(nonCriticalResult.flags().contains("First-timer"));

        // Critical task: first-timer bonus must NOT apply
        SolverTask criticalTask = new SolverTask(
                UUID.randomUUID(), "Coding Session Critical", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Java"), List.of(), true // critical
        );
        FitResult criticalResult = ScoringService.score(firstTimer, criticalTask, configWithBonus);

        // Arithmetic hand calculation for critical task (weights: 900/23, 450/23, 450/23, 300/23, 200/23):
        // fit = (900/23 * 0.50) + (450/23 * 0.0) + (450/23 * 0.5) + (300/23 * 1.0) + 0.0
        //     = (450.0 + 0.0 + 225.0 + 300.0) / 23.0
        //     = 975.0 / 23.0 ≈ 42.3913043478
        // No bonus applied because task is critical!
        double expectedCriticalFit = 975.0 / 23.0;
        assertEquals(expectedCriticalFit, criticalResult.fit(), 1e-9);
        assertTrue(criticalResult.flags().contains("Critical task"));
        assertFalse(criticalResult.flags().contains("First-timer"));

        // Cap at 100: perfect non-critical volunteer (base fit = 97.5) + bonus 15 = 112.5 -> capped to 100.0
        SolverVolunteer nearPerfectFirstTimer = new SolverVolunteer(
                UUID.randomUUID(), "Grace",
                List.of(new SkillLevel("Java", "expert")), // expert = 1.0
                0, // pastEventsCount = 0 -> exp = 0.0
                5.0, // averageRating = 5.0 -> perf = 1.0
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8,
                List.of("Coding") // matches title word "Coding" -> interest = 1.0
        );
        // Base fit = 45*1.0 + 15*0.0 + 15*1.0 + 15*1.0 + 10*1.0 = 45 + 15 + 15 + 10 = 85.0
        // With bonus 20: 85.0 + 20.0 = 105.0 -> capped at 100.0
        ScoringConfig bonus20Config = new ScoringConfig(DEFAULT_WEIGHTS, 20, 8);
        FitResult cappedResult = ScoringService.score(nearPerfectFirstTimer, nonCriticalTask, bonus20Config);

        // Arithmetic hand calculation: min(100.0, 85.0 + 20.0) = 100.0
        assertEquals(100.0, cappedResult.fit(), 1e-9);
        assertTrue(cappedResult.flags().contains("First-timer"));
    }

    @Test
    @DisplayName("Case-insensitive and trimmed skill and interest matching")
    void caseInsensitiveAndTrimmedMatching() {
        LocalDate monday = LocalDate.of(2026, 11, 16);

        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Hannah",
                List.of(new SkillLevel("  pYtHon  ", "  AdVaNcEd  ")), // advanced = 0.75
                5, // exp = 1.0
                5.0, // perf = 1.0
                List.of(new AvailabilitySlot("  Monday  ", "  Morning  ")),
                8,
                List.of("  dEvOpS  ") // interest = 1.0
        );

        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Annual DevOps Conference", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("  Python  "), List.of(), false
        );

        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);
        assertTrue(result.feasible());

        // Arithmetic hand calculation:
        // skillFactor = 0.75, expFactor = 1.0, perfFactor = 1.0, loadFactor = 1.0, interestFactor = 1.0
        // Base weights: 45 / 15 / 15 / 15 / 10
        // fit = (45 * 0.75) + (15 * 1.0) + (15 * 1.0) + (15 * 1.0) + (10 * 1.0)
        //     = 33.75 + 15.0 + 15.0 + 15.0 + 10.0 = 88.75
        assertEquals(88.75, result.fit(), 1e-9);
        assertEquals(0.75, result.breakdown().skill(), 1e-9);
        assertEquals(1.0, result.breakdown().interest(), 1e-9);
    }

    @Test
    @DisplayName("Constructor validation: weights not summing to 100, negative weight, null list, rating 0 or 6 -> IllegalArgumentException")
    void constructorValidation() {
        // Weights not summing to 100
        assertThrows(IllegalArgumentException.class, () -> new ScoreWeights(30, 20, 20, 20, 20)); // sum 110
        assertThrows(IllegalArgumentException.class, () -> new ScoreWeights(20, 20, 20, 20, 10)); // sum 90

        // Negative weight
        assertThrows(IllegalArgumentException.class, () -> new ScoreWeights(-10, 30, 30, 30, 20));

        // Rating out of [1.0, 5.0] range
        assertThrows(IllegalArgumentException.class, () -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, 0.0, List.of(), 8, List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, 6.0, List.of(), 8, List.of()
        ));
        // Valid ratings
        assertDoesNotThrow(() -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, null, List.of(), 8, List.of()
        ));
        assertDoesNotThrow(() -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, 1.0, List.of(), 8, List.of()
        ));
        assertDoesNotThrow(() -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, 5.0, List.of(), 8, List.of()
        ));

        // Null list in SolverVolunteer
        assertThrows(IllegalArgumentException.class, () -> new SolverVolunteer(
                UUID.randomUUID(), "Name", null, 0, 4.0, List.of(), 8, List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, 4.0, null, 8, List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SolverVolunteer(
                UUID.randomUUID(), "Name", List.of(), 0, 4.0, List.of(), 8, null
        ));

        // Null list in SolverTask
        LocalDate monday = LocalDate.of(2026, 11, 16);
        assertThrows(IllegalArgumentException.class, () -> new SolverTask(
                UUID.randomUUID(), "Title", monday, LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                null, List.of(), false
        ));
        assertThrows(IllegalArgumentException.class, () -> new SolverTask(
                UUID.randomUUID(), "Title", monday, LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of(), null, false
        ));

        // Config validation
        assertThrows(IllegalArgumentException.class, () -> new ScoringConfig(DEFAULT_WEIGHTS, -1, 8));
        assertThrows(IllegalArgumentException.class, () -> new ScoringConfig(DEFAULT_WEIGHTS, 21, 8));
        assertThrows(IllegalArgumentException.class, () -> new ScoringConfig(DEFAULT_WEIGHTS, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScoringConfig(DEFAULT_WEIGHTS, 10, 25));
    }

    @Test
    @DisplayName("Determinism: scoring the same input 1000 times gives identical results")
    void determinism_1000Runs() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Deterministic Volunteer",
                List.of(new SkillLevel("Java", "advanced")),
                3, 4.2,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                6, List.of("Java")
        );
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Java Development", monday,
                LocalTime.of(9, 0), LocalTime.of(12, 0), 1,
                List.of("Java"), List.of(), false
        );

        FitResult baseline = ScoringService.score(volunteer, task, DEFAULT_CONFIG);

        for (int i = 0; i < 1000; i++) {
            FitResult run = ScoringService.score(volunteer, task, DEFAULT_CONFIG);
            assertEquals(baseline.feasible(), run.feasible());
            assertEquals(baseline.infeasibleReason(), run.infeasibleReason());
            // Arithmetic hand calculation: identical runs must match exactly with 1e-9 tolerance
            assertEquals(baseline.fit(), run.fit(), 1e-9);
            assertEquals(baseline.breakdown().skill(), run.breakdown().skill(), 1e-9);
            assertEquals(baseline.breakdown().experience(), run.breakdown().experience(), 1e-9);
            assertEquals(baseline.breakdown().performance(), run.breakdown().performance(), 1e-9);
            assertEquals(baseline.breakdown().load(), run.breakdown().load(), 1e-9);
            assertEquals(baseline.breakdown().interest(), run.breakdown().interest(), 1e-9);
            assertEquals(baseline.flags(), run.flags());
        }
    }

    @Test
    @DisplayName("Inputs are not mutated (compare before and after)")
    void inputsNotMutated() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        UUID vId = UUID.randomUUID();
        List<SkillLevel> skills = List.of(new SkillLevel("Java", "expert"));
        List<AvailabilitySlot> avail = List.of(new AvailabilitySlot("Monday", "Morning"));
        List<String> interests = List.of("Backend");

        SolverVolunteer volunteer = new SolverVolunteer(vId, "Ian", skills, 4, 4.5, avail, 8, interests);

        UUID tId = UUID.randomUUID();
        List<String> mandatory = List.of("Java");
        List<String> preferred = List.of("Spring");
        SolverTask task = new SolverTask(tId, "Backend Dev", monday, LocalTime.of(9, 0), LocalTime.of(12, 0), 2, mandatory, preferred, true);

        // Capture snapshot before
        int beforePastEvents = volunteer.pastEventsCount();
        Double beforeRating = volunteer.averageRating();
        int beforeMaxHours = volunteer.maxHours();
        String beforeName = volunteer.name();

        ScoringService.score(volunteer, task, DEFAULT_CONFIG);

        // Assert volunteer remained unchanged
        assertEquals(vId, volunteer.id());
        assertEquals(beforeName, volunteer.name());
        assertEquals(skills, volunteer.skills());
        assertEquals(beforePastEvents, volunteer.pastEventsCount());
        assertEquals(beforeRating, volunteer.averageRating());
        assertEquals(avail, volunteer.availability());
        assertEquals(beforeMaxHours, volunteer.maxHours());
        assertEquals(interests, volunteer.interests());

        // Assert task remained unchanged
        assertEquals(tId, task.id());
        assertEquals("Backend Dev", task.title());
        assertEquals(monday, task.date());
        assertEquals(LocalTime.of(9, 0), task.startTime());
        assertEquals(LocalTime.of(12, 0), task.endTime());
        assertEquals(2, task.headcount());
        assertEquals(mandatory, task.mandatorySkills());
        assertEquals(preferred, task.preferredSkills());
        assertTrue(task.critical());

        // Assert config weights unchanged
        assertEquals(DEFAULT_WEIGHTS, DEFAULT_CONFIG.weights());
    }

    @Test
    @DisplayName("Property test with seeded java.util.Random (fixed seed 42, 2000 random cases)")
    void propertyTest_seededRandom_2000Cases() {
        Random random = new Random(42);
        String[] possibleSkills = {"Java", "Python", "Docker", "Audio", "Lighting", "Stage", "Security", "Design"};
        String[] possibleLevels = {"beginner", "intermediate", "advanced", "expert"};
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String[] slots = {"Morning", "Afternoon", "Evening"};

        for (int i = 0; i < 2000; i++) {
            // Generate random volunteer
            int numSkills = random.nextInt(4);
            List<SkillLevel> vSkills = new ArrayList<>();
            for (int s = 0; s < numSkills; s++) {
                vSkills.add(new SkillLevel(
                        possibleSkills[random.nextInt(possibleSkills.length)],
                        possibleLevels[random.nextInt(possibleLevels.length)]
                ));
            }
            int pastEvents = random.nextInt(20);
            Double rating = random.nextBoolean() ? null : (1.0 + 4.0 * random.nextDouble());
            int numSlots = 1 + random.nextInt(4);
            List<AvailabilitySlot> vSlots = new ArrayList<>();
            for (int sl = 0; sl < numSlots; sl++) {
                vSlots.add(new AvailabilitySlot(days[random.nextInt(days.length)], slots[random.nextInt(slots.length)]));
            }
            int maxHours = random.nextInt(12); // 0 to 11
            int numInterests = random.nextInt(3);
            List<String> interests = new ArrayList<>();
            for (int in = 0; in < numInterests; in++) {
                interests.add(possibleSkills[random.nextInt(possibleSkills.length)]);
            }

            SolverVolunteer volunteer = new SolverVolunteer(
                    UUID.randomUUID(), "Vol " + i, vSkills, pastEvents, rating, vSlots, maxHours, interests
            );

            // Generate random task
            LocalDate date = LocalDate.of(2026, 11, 16).plusDays(random.nextInt(7));
            int startHour = random.nextInt(20);
            int startMin = random.nextInt(60);
            LocalTime startTime = LocalTime.of(startHour, startMin);
            int durationMinutes = 30 + random.nextInt(360); // 30m to 6.5h
            LocalTime endTime = startTime.plusMinutes(durationMinutes);

            int numMandatory = random.nextInt(3);
            List<String> mandatory = new ArrayList<>();
            for (int m = 0; m < numMandatory; m++) {
                mandatory.add(possibleSkills[random.nextInt(possibleSkills.length)]);
            }
            int numPreferred = random.nextInt(3);
            List<String> preferred = new ArrayList<>();
            for (int p = 0; p < numPreferred; p++) {
                preferred.add(possibleSkills[random.nextInt(possibleSkills.length)]);
            }
            boolean critical = random.nextBoolean();

            SolverTask task = new SolverTask(
                    UUID.randomUUID(), "Task " + i + " Audio Setup", date, startTime, endTime,
                    1 + random.nextInt(5), mandatory, preferred, critical
            );

            // Random valid config
            int w1 = random.nextInt(21);
            int w2 = random.nextInt(21);
            int w3 = random.nextInt(21);
            int w4 = random.nextInt(21);
            int w5 = 100 - (w1 + w2 + w3 + w4);
            if (w5 < 0) {
                w5 = 0;
                w1 = 100 - (w2 + w3 + w4);
            }
            ScoreWeights weights = new ScoreWeights(w1, w2, w3, w4, w5);
            ScoringConfig config = new ScoringConfig(weights, random.nextInt(21), 1 + random.nextInt(12));

            FitResult result = ScoringService.score(volunteer, task, config);

            // Invariants assertion
            assertNotNull(result);
            assertTrue(result.fit() >= 0.0 && result.fit() <= 100.0,
                    "Fit score must be within [0.0, 100.0], got: " + result.fit());

            if (result.feasible()) {
                assertNull(result.infeasibleReason(), "Infeasible reason must be null when feasible");
                assertNotNull(result.breakdown(), "Breakdown must not be null when feasible");
                FitBreakdown bd = result.breakdown();
                assertTrue(bd.skill() >= 0.0 && bd.skill() <= 1.0, "Skill breakdown within [0, 1]");
                assertTrue(bd.experience() >= 0.0 && bd.experience() <= 1.0, "Experience breakdown within [0, 1]");
                assertTrue(bd.performance() >= 0.0 && bd.performance() <= 1.0, "Performance breakdown within [0, 1]");
                assertTrue(bd.load() >= 0.0 && bd.load() <= 1.0, "Load breakdown within [0, 1]");
                assertTrue(bd.interest() >= 0.0 && bd.interest() <= 1.0, "Interest breakdown within [0, 1]");
            } else {
                // Arithmetic hand calculation: Infeasible fit is strictly 0.0
                assertEquals(0.0, result.fit(), 1e-9, "Fit must be 0.0 when infeasible");
                assertNotNull(result.infeasibleReason(), "Infeasible reason must be non-null when infeasible");
                assertNull(result.breakdown(), "Breakdown must be null when infeasible");
            }
        }
    }
}
