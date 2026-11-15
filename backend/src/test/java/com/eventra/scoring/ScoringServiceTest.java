package com.eventra.scoring;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
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
import java.util.Arrays;
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
                List.of(new SkillLevel("Sound", "Advanced")),
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
                List.of(new SkillLevel("Java", "Intermediate")), // Intermediate = 2/3
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
        //   Volunteer has "Java" at Intermediate (2/3) and lacks "Docker" (0.0)
        //   skillFactor = (2/3 + 0.0) / 2 = 1/3
        // - pastEventsCount = 2 -> experienceFactor = 2 / 5.0 = 0.4
        // - averageRating = null -> performanceFactor = 0.5
        // - loadFactor = 1.0
        // - interest "Music" matches neither skills ("Java", "Docker") nor title words
        //   -> interestFactor = 0.0
        // Base weights: wS=45, wE=15, wP=15, wL=15, wI=10
        // fit = (45 * (1/3)) + (15 * 0.4) + (15 * 0.5) + (15 * 1.0) + (10 * 0.0)
        //     = 15.0 + 6.0 + 7.5 + 15.0 + 0.0
        //     = 43.5
        // pastEventsCount = 2 != 0 -> firstTimerBonus = 0
        // Total expected fit = 43.5
        assertEquals(43.5, result.fit(), 1e-9);

        assertNotNull(result.breakdown());
        assertEquals(1.0 / 3.0, result.breakdown().skill(), 1e-9);
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
                List.of(new SkillLevel("Lighting", "Advanced")),
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

        // 4 hours and 1 minute: 08:00 to 12:01 -> 241 minutes > 4 hours -> infeasible
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
                List.of(new SkillLevel("Audio", "Beginner")), // Beginner = 1/3
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
        // skillFactor = 1/3, expFactor = 1.0, perfFactor = 1.0, loadFactor = 1.0, interestFactor = 0.0
        // nonCritical fit = (45 * (1/3)) + (15 * 1.0) + (15 * 1.0) + (15 * 1.0) + (10 * 0.0)
        //                 = 15.0 + 15.0 + 15.0 + 15.0 + 0.0 = 60.0
        assertEquals(60.0, nonCriticalResult.fit(), 1e-9);

        // Arithmetic hand calculation for critical task:
        // Base weights: wS = 45, wE = 15, wP = 15, wL = 15, wI = 10
        // Multiply experience and performance by 1.5:
        // wE' = 15 * 1.5 = 22.5
        // wP' = 15 * 1.5 = 22.5
        // Sum of unnormalized weights = 45 + 22.5 + 22.5 + 15 + 10 = 115.0
        // Rescaled weights (each multiplied by 100 / 115.0):
        // wS_rescaled = (45 / 115.0) * 100.0 = 4500.0 / 115.0 = 900.0 / 23.0 ~ 39.1304347826
        // wE_rescaled = (22.5 / 115.0) * 100.0 = 2250.0 / 115.0 = 450.0 / 23.0 ~ 19.5652173913
        // wP_rescaled = (22.5 / 115.0) * 100.0 = 2250.0 / 115.0 = 450.0 / 23.0 ~ 19.5652173913
        // wL_rescaled = (15 / 115.0) * 100.0 = 1500.0 / 115.0 = 300.0 / 23.0 ~ 13.0434782608
        // wI_rescaled = (10 / 115.0) * 100.0 = 1000.0 / 115.0 = 200.0 / 23.0 ~ 8.6956521739
        // Sum check: (900 + 450 + 450 + 300 + 200) / 23.0 = 2300.0 / 23.0 = 100.0 exactly!
        double rescaledSum = (900.0 / 23.0) + (450.0 / 23.0) + (450.0 / 23.0) + (300.0 / 23.0) + (200.0 / 23.0);
        assertEquals(100.0, rescaledSum, 1e-9);

        // Critical fit = (900.0 / 23.0) * (1/3) + (450.0 / 23.0) * 1.0 + (450.0 / 23.0) * 1.0
        //              + (300.0 / 23.0) * 1.0 + (200.0 / 23.0) * 0.0
        //              = (300.0 + 450.0 + 450.0 + 300.0) / 23.0
        //              = 1500.0 / 23.0 ~ 65.2173913
        double expectedCriticalFit = 1500.0 / 23.0;
        assertEquals(expectedCriticalFit, criticalResult.fit(), 1e-9);

        // Verification of expected direction: 65.217... > 60.0 (increased due to higher exp & perf weighting)
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
                List.of(new SkillLevel("Java", "Intermediate")), // Intermediate = 2/3
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
        // skillFactor = 2/3, expFactor = 0.0, perfFactor = 0.5, loadFactor = 1.0, interestFactor = 0.0
        // Base fit = (45 * (2/3)) + (15 * 0.0) + (15 * 0.5) + (15 * 1.0) + (10 * 0.0)
        //          = 30.0 + 0.0 + 7.5 + 15.0 + 0.0 = 52.5
        // firstTimerBonus = 15 -> fit with bonus = 52.5 + 15.0 = 67.5
        assertEquals(67.5, nonCriticalResult.fit(), 1e-9);
        assertTrue(nonCriticalResult.flags().contains("First-timer"));

        // Critical task: first-timer bonus must NOT apply
        SolverTask criticalTask = new SolverTask(
                UUID.randomUUID(), "Coding Session Critical", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Java"), List.of(), true // critical
        );
        FitResult criticalResult = ScoringService.score(firstTimer, criticalTask, configWithBonus);

        // Arithmetic hand calculation for critical task (weights: 900/23, 450/23, 450/23, 300/23, 200/23):
        // fit = (900/23 * (2/3)) + (450/23 * 0.0) + (450/23 * 0.5) + (300/23 * 1.0) + 0.0
        //     = (600.0 + 0.0 + 225.0 + 300.0) / 23.0
        //     = 1125.0 / 23.0 ~ 48.913043478
        // No bonus applied because task is critical!
        double expectedCriticalFit = 1125.0 / 23.0;
        assertEquals(expectedCriticalFit, criticalResult.fit(), 1e-9);
        assertTrue(criticalResult.flags().contains("Critical task"));
        assertFalse(criticalResult.flags().contains("First-timer"));

        // Cap test: uses Java Advanced, base 85.0, bonus 20, capped 100.0
        SolverVolunteer nearPerfectFirstTimer = new SolverVolunteer(
                UUID.randomUUID(), "Grace",
                List.of(new SkillLevel("Java", "Advanced")), // Advanced = 1.0
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
                List.of(new SkillLevel("  pYtHon  ", "  InTeRmEdIaTe  ")), // Intermediate = 2/3
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
        // skillFactor = 2/3, expFactor = 1.0, perfFactor = 1.0, loadFactor = 1.0, interestFactor = 1.0
        // Base weights: 45 / 15 / 15 / 15 / 10
        // fit = (45 * (2/3)) + (15 * 1.0) + (15 * 1.0) + (15 * 1.0) + (10 * 1.0)
        //     = 30.0 + 15.0 + 15.0 + 15.0 + 10.0 = 85.0
        assertEquals(85.0, result.fit(), 1e-9);
        assertEquals(2.0 / 3.0, result.breakdown().skill(), 1e-9);
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
                List.of(new SkillLevel("Java", "Advanced")),
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
            // Exact equality on doubles with no delta
            assertEquals(baseline.fit(), run.fit());
            assertEquals(baseline.breakdown().skill(), run.breakdown().skill());
            assertEquals(baseline.breakdown().experience(), run.breakdown().experience());
            assertEquals(baseline.breakdown().performance(), run.breakdown().performance());
            assertEquals(baseline.breakdown().load(), run.breakdown().load());
            assertEquals(baseline.breakdown().interest(), run.breakdown().interest());
            assertEquals(baseline.flags(), run.flags());
        }
    }

    @Test
    @DisplayName("Inputs are not mutated (compare before and after)")
    void inputsNotMutated() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        UUID vId = UUID.randomUUID();
        List<SkillLevel> skills = List.of(new SkillLevel("Java", "Advanced"));
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
    @DisplayName("Level mapping: Beginner=1/3, Intermediate=2/3, Advanced=1.0; Expert and null give 0.0 (feasible)")
    void levelMapping_exactValues() {
        LocalDate date = LocalDate.of(2026, 11, 16); // Monday
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);
        List<AvailabilitySlot> avail = List.of(new AvailabilitySlot("Monday", "Morning"));
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Task", date, start, end, 1,
                List.of("SkillA"), List.of(), false
        );

        // Beginner -> 1/3
        SolverVolunteer vBeginner = new SolverVolunteer(
                UUID.randomUUID(), "V1", List.of(new SkillLevel("SkillA", "Beginner")),
                0, null, avail, 8, List.of()
        );
        FitResult rBeginner = ScoringService.score(vBeginner, task, DEFAULT_CONFIG);
        assertTrue(rBeginner.feasible());
        assertEquals(1.0 / 3.0, rBeginner.breakdown().skill(), 1e-9);

        // Intermediate -> 2/3
        SolverVolunteer vInter = new SolverVolunteer(
                UUID.randomUUID(), "V2", List.of(new SkillLevel("SkillA", "Intermediate")),
                0, null, avail, 8, List.of()
        );
        FitResult rInter = ScoringService.score(vInter, task, DEFAULT_CONFIG);
        assertTrue(rInter.feasible());
        assertEquals(2.0 / 3.0, rInter.breakdown().skill(), 1e-9);

        // Advanced -> 1.0
        SolverVolunteer vAdv = new SolverVolunteer(
                UUID.randomUUID(), "V3", List.of(new SkillLevel("SkillA", "Advanced")),
                0, null, avail, 8, List.of()
        );
        FitResult rAdv = ScoringService.score(vAdv, task, DEFAULT_CONFIG);
        assertTrue(rAdv.feasible());
        assertEquals(1.0, rAdv.breakdown().skill(), 1e-9);

        // Expert -> 0.0 and still feasible
        SolverVolunteer vExpert = new SolverVolunteer(
                UUID.randomUUID(), "V4", List.of(new SkillLevel("SkillA", "Expert")),
                0, null, avail, 8, List.of()
        );
        FitResult rExpert = ScoringService.score(vExpert, task, DEFAULT_CONFIG);
        assertTrue(rExpert.feasible());
        assertEquals(0.0, rExpert.breakdown().skill(), 1e-9);

        // null level -> 0.0 and still feasible
        SolverVolunteer vNullLevel = new SolverVolunteer(
                UUID.randomUUID(), "V5", List.of(new SkillLevel("SkillA", null)),
                0, null, avail, 8, List.of()
        );
        FitResult rNullLevel = ScoringService.score(vNullLevel, task, DEFAULT_CONFIG);
        assertTrue(rNullLevel.feasible());
        assertEquals(0.0, rNullLevel.breakdown().skill(), 1e-9);
    }

    @Test
    @DisplayName("Overnight: Monday Evening availability, task 22:00-02:00 (4 hours): feasible with maxHours 4, infeasible with maxHours 3")
    void overnight_durationCheck() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(22, 0); // 22:00 is Evening
        LocalTime end = LocalTime.of(2, 0);    // runs overnight, 4 hours (240 minutes)

        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Night Shift", monday, start, end, 1,
                List.of(), List.of(), false
        );

        // Volunteer with maxHours 4 -> feasible
        SolverVolunteer v4h = new SolverVolunteer(
                UUID.randomUUID(), "Vol4h", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Evening")), 4, List.of()
        );
        FitResult r4h = ScoringService.score(v4h, task, DEFAULT_CONFIG);
        assertTrue(r4h.feasible());

        // Volunteer with maxHours 3 -> infeasible
        SolverVolunteer v3h = new SolverVolunteer(
                UUID.randomUUID(), "Vol3h", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Evening")), 3, List.of()
        );
        FitResult r3h = ScoringService.score(v3h, task, DEFAULT_CONFIG);
        assertFalse(r3h.feasible());
        assertEquals("Over hours: task duration 240 min exceeds max 3 h", r3h.infeasibleReason());
    }

    @Test
    @DisplayName("Zero duration: task 09:00-09:00 is feasible with maxHours 1")
    void zeroDuration_feasible() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime time = LocalTime.of(9, 0); // Morning
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Instant Task", monday, time, time, 1,
                List.of(), List.of(), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Vol1h", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 1, List.of()
        );
        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);
        assertTrue(result.feasible());
        assertNull(result.infeasibleReason());
    }

    @Test
    @DisplayName("Priority: volunteer missing mandatory skill AND unavailable gets missing-skill reason; critical infeasible carries flag")
    void priority_andCriticalInfeasibleFlag() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        // Volunteer is available on Tuesday Morning, but task is Monday Morning.
        // Volunteer has no skills, but task requires mandatory "Security".
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "MissingAndUnavailable", List.of(), 1, 4.0,
                List.of(new AvailabilitySlot("Tuesday", "Morning")), 8, List.of()
        );

        // Non-critical task: priority check gives missing-skill reason
        SolverTask nonCriticalTask = new SolverTask(
                UUID.randomUUID(), "Security Duty", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Security"), List.of(), false
        );
        FitResult rNonCritical = ScoringService.score(volunteer, nonCriticalTask, DEFAULT_CONFIG);
        assertFalse(rNonCritical.feasible());
        assertEquals("Missing mandatory skill: Security", rNonCritical.infeasibleReason());
        assertFalse(rNonCritical.flags().contains("Critical task"));

        // Critical task: infeasible result carries the flag "Critical task"
        SolverTask criticalTask = new SolverTask(
                UUID.randomUUID(), "Critical Security Duty", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Security"), List.of(), true
        );
        FitResult rCritical = ScoringService.score(volunteer, criticalTask, DEFAULT_CONFIG);
        assertFalse(rCritical.feasible());
        assertEquals("Missing mandatory skill: Security", rCritical.infeasibleReason());
        assertTrue(rCritical.flags().contains("Critical task"));
    }

    @Test
    @DisplayName("Duplicates: mandatory [Java], preferred [java, Docker], volunteer Java Advanced gives skill 0.5")
    void duplicates_mandatoryAndPreferred() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Coding Task", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of("Java"), List.of("java", "Docker"), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Java Pro",
                List.of(new SkillLevel("Java", "Advanced")), // 1.0
                1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 8, List.of()
        );
        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);
        assertTrue(result.feasible());
        // Deduplicated skills are ["Java", "Docker"]. Java=1.0, Docker=0.0 -> average = 0.5
        assertEquals(0.5, result.breakdown().skill(), 1e-9);
    }

    @Test
    @DisplayName("Interest matching ignores punctuation: title 'Setup, Logistics!' with interest 'logistics' gives interest 1.0")
    void interestMatching_ignoresPunctuation() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Setup, Logistics!", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of(), List.of(), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Logistics Fan", List.of(),
                1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 8,
                List.of("logistics")
        );
        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);
        assertTrue(result.feasible());
        assertEquals(1.0, result.breakdown().interest(), 1e-9);
    }

    @Test
    @DisplayName("Unicode title stripping: title 'Caf\u00e9, Crew!' with interest 'caf\u00e9' gives interest 1.0")
    void unicodeTitleStripping_cafeCrew() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        SolverTask task = new SolverTask(
                UUID.randomUUID(), "Caf\u00e9, Crew!", monday,
                LocalTime.of(9, 0), LocalTime.of(11, 0), 1,
                List.of(), List.of(), false
        );
        SolverVolunteer volunteer = new SolverVolunteer(
                UUID.randomUUID(), "Cafe Volunteer", List.of(),
                1, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")), 8,
                List.of("caf\u00e9")
        );
        FitResult result = ScoringService.score(volunteer, task, DEFAULT_CONFIG);
        assertTrue(result.feasible());
        assertEquals(1.0, result.breakdown().interest(), 1e-9);
    }

    @Test
    @DisplayName("Property test with seeded java.util.Random (fixed seed 42, 2000 random cases)")
    void propertyTest_seededRandom_2000Cases() {
        Random random = new Random(42);
        String[] possibleSkills = {"Java", "Python", "Docker", "Audio", "Lighting"};
        List<String> possibleLevels = ModelValues.SKILL_LEVELS;
        List<String> days = ModelValues.DAYS;
        List<String> slots = ModelValues.SLOTS;

        int feasibleCount = 0;
        int infeasibleCount = 0;
        int[] feasiblePerDay = new int[7];

        for (int i = 0; i < 2000; i++) {
            // Generate random volunteer: 2 to 7 skills
            int numSkills = 2 + random.nextInt(6);
            List<SkillLevel> vSkills = new ArrayList<>();
            for (int s = 0; s < numSkills; s++) {
                vSkills.add(new SkillLevel(
                        possibleSkills[random.nextInt(possibleSkills.length)],
                        possibleLevels.get(random.nextInt(possibleLevels.size()))
                ));
            }
            int pastEvents = random.nextInt(20);
            Double rating = random.nextBoolean() ? null : (1.0 + 4.0 * random.nextDouble());
            // Availability slots: 4 + nextInt(18) (4 to 21) across all 7 weekdays
            int numSlots = 4 + random.nextInt(18);
            List<AvailabilitySlot> vSlots = new ArrayList<>();
            for (int sl = 0; sl < numSlots; sl++) {
                vSlots.add(new AvailabilitySlot(
                        days.get(random.nextInt(days.size())),
                        slots.get(random.nextInt(slots.size()))
                ));
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

            // Generate random task across all 7 weekdays
            LocalDate date = LocalDate.of(2026, 11, 16).plusDays(random.nextInt(7));
            int startHour = random.nextInt(24);
            int startMin = random.nextInt(60);
            LocalTime startTime = LocalTime.of(startHour, startMin);
            // durationMinutes nextInt(300)
            int durationMinutes = random.nextInt(300);
            LocalTime endTime = startTime.plusMinutes(durationMinutes);

            // mandatory skills nextInt(2)
            int numMandatory = random.nextInt(2);
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

            // Independent oracle verification
            boolean oracleFeasible = computeFeasibilityOracle(volunteer, task, config);
            assertEquals(oracleFeasible, result.feasible(),
                    "Oracle and ScoringService feasibility mismatch at iteration " + i);

            if (result.feasible()) {
                feasibleCount++;
                int dayIndex = task.date().getDayOfWeek().getValue() - 1;
                feasiblePerDay[dayIndex]++;
                assertNull(result.infeasibleReason(), "Infeasible reason must be null when feasible");
                assertNotNull(result.breakdown(), "Breakdown must not be null when feasible");
                FitBreakdown bd = result.breakdown();
                assertTrue(bd.skill() >= 0.0 && bd.skill() <= 1.0, "Skill breakdown within [0, 1]");
                assertTrue(bd.experience() >= 0.0 && bd.experience() <= 1.0, "Experience breakdown within [0, 1]");
                assertTrue(bd.performance() >= 0.0 && bd.performance() <= 1.0, "Performance breakdown within [0, 1]");
                assertTrue(bd.load() >= 0.0 && bd.load() <= 1.0, "Load breakdown within [0, 1]");
                assertTrue(bd.interest() >= 0.0 && bd.interest() <= 1.0, "Interest breakdown within [0, 1]");

                // Independent fit recomputation from result.breakdown() and config
                double wSkill = (double) config.weights().skill();
                double wExp = (double) config.weights().experience();
                double wPerf = (double) config.weights().performance();
                double wLoad = (double) config.weights().load();
                double wInterest = (double) config.weights().interest();

                if (task.critical()) {
                    wExp *= 1.5;
                    wPerf *= 1.5;
                    double weightSum = wSkill + wExp + wPerf + wLoad + wInterest;
                    wSkill = (wSkill / weightSum) * 100.0;
                    wExp = (wExp / weightSum) * 100.0;
                    wPerf = (wPerf / weightSum) * 100.0;
                    wLoad = (wLoad / weightSum) * 100.0;
                    wInterest = (wInterest / weightSum) * 100.0;
                }

                double expected = (wSkill * bd.skill())
                        + (wExp * bd.experience())
                        + (wPerf * bd.performance())
                        + (wLoad * bd.load())
                        + (wInterest * bd.interest());

                if (!task.critical() && volunteer.pastEventsCount() == 0) {
                    expected += config.firstTimerBonus();
                }

                if (expected > 100.0) {
                    expected = 100.0;
                } else if (expected < 0.0) {
                    expected = 0.0;
                }

                assertEquals(expected, result.fit(), 1e-9);
            } else {
                infeasibleCount++;
                assertEquals(0.0, result.fit(), 1e-9, "Fit must be 0.0 when infeasible");
                assertNotNull(result.infeasibleReason(), "Infeasible reason must be non-null when infeasible");
                assertNull(result.breakdown(), "Breakdown must be null when infeasible");
            }

            // Invariants assertion
            assertNotNull(result);
            assertTrue(result.fit() >= 0.0 && result.fit() <= 100.0,
                    "Fit score must be within [0.0, 100.0], got: " + result.fit());
        }

        System.out.println("Property test counts: feasible=" + feasibleCount + ", infeasible=" + infeasibleCount);
        System.out.println("Per-day feasible counts: " + Arrays.toString(feasiblePerDay));
        assertTrue(feasibleCount >= 500, "Feasible count must be at least 500, was: " + feasibleCount);
        assertTrue(infeasibleCount >= 500, "Infeasible count must be at least 500, was: " + infeasibleCount);
        for (int d = 0; d < 7; d++) {
            assertTrue(feasiblePerDay[d] >= 30,
                    "Feasible count for day index " + d + " (" + ModelValues.DAYS.get(d) + ") must be at least 30, was: " + feasiblePerDay[d]);
        }
    }

    /**
     * Independent oracle that recomputes feasibility without calling ScoringService.
     */
    private boolean computeFeasibilityOracle(SolverVolunteer volunteer, SolverTask task, ScoringConfig config) {
        // 1. Mandatory skills present
        for (String mandatory : task.mandatorySkills()) {
            String trimmedMandatory = mandatory.trim();
            boolean hasSkill = false;
            for (SkillLevel sl : volunteer.skills()) {
                if (sl != null && sl.getName() != null && sl.getName().trim().equalsIgnoreCase(trimmedMandatory)) {
                    hasSkill = true;
                    break;
                }
            }
            if (!hasSkill) {
                return false;
            }
        }

        // 2. Availability by weekday index and raw seconds slot
        String expectedDay = task.date().getDayOfWeek().name();
        int s = task.startTime().toSecondOfDay();
        String expectedSlot;
        if (5 * 3600 <= s && s < 12 * 3600) {
            expectedSlot = "Morning";
        } else if (12 * 3600 <= s && s < 17 * 3600) {
            expectedSlot = "Afternoon";
        } else {
            expectedSlot = "Evening";
        }

        boolean available = false;
        for (AvailabilitySlot slot : volunteer.availability()) {
            if (slot != null && slot.getDay() != null && slot.getSlot() != null) {
                if (slot.getDay().trim().equalsIgnoreCase(expectedDay)
                        && slot.getSlot().trim().equalsIgnoreCase(expectedSlot)) {
                    available = true;
                    break;
                }
            }
        }
        if (!available) {
            return false;
        }

        // 3. Duration: raw second arithmetic modulo 86400
        long durSec = Math.floorMod((long) task.endTime().toSecondOfDay() - task.startTime().toSecondOfDay(), 86400L);
        long maxHours = volunteer.maxHours() > 0 ? volunteer.maxHours() : config.defaultMaxHours();
        if (durSec > maxHours * 3600L) {
            return false;
        }

        return true;
    }
}
