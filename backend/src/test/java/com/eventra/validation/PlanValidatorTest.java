package com.eventra.validation;

import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ModelValues;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.model.FitBreakdown;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import com.eventra.solver.AssignmentSolver;
import com.eventra.solver.model.AssignedPair;
import com.eventra.solver.model.SolverResult;
import com.eventra.solver.model.UnfilledTask;
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
 * Unit and property tests for PlanValidator.
 */
class PlanValidatorTest {

    private static final ScoreWeights DEFAULT_WEIGHTS = new ScoreWeights(45, 15, 15, 15, 10);
    private static final ScoringConfig DEFAULT_CONFIG = new ScoringConfig(DEFAULT_WEIGHTS, 10, 8);
    private static final String[] POSSIBLE_SKILLS = {"Java", "Python", "Docker", "Audio", "Lighting"};

    @Test
    @DisplayName("f) PlanValidator: valid solver output on 200 seeded cases (seed 17) returns empty list")
    void validSolverOutput_200Cases() {
        Random rng = new Random(17);

        for (int c = 0; c < 200; c++) {
            int numVolunteers = 1 + rng.nextInt(10);
            int numTasks = 1 + rng.nextInt(5);

            List<SolverVolunteer> volunteers = new ArrayList<>();
            for (int v = 0; v < numVolunteers; v++) {
                volunteers.add(generateRandomVolunteer(rng, "ValVol-" + c + "-" + v));
            }

            List<SolverTask> tasks = new ArrayList<>();
            for (int t = 0; t < numTasks; t++) {
                tasks.add(generateRandomTask(rng, "ValTask-" + c + "-" + t, 1 + rng.nextInt(3)));
            }

            SolverResult result = AssignmentSolver.solve(volunteers, tasks, DEFAULT_CONFIG);
            List<String> violations = PlanValidator.validate(volunteers, tasks, DEFAULT_CONFIG, result);
            assertTrue(violations.isEmpty(), "Case " + c + " produced unexpected violations: " + violations);
        }
    }

    // -------------------------------------------------------------------------
    // TEST (f): Hand-built Corrupted Results (split into focused methods 1 to 11)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("f1) Hand-built violation: duplicate volunteer assignment")
    void duplicateVolunteer() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverVolunteer v2 = new SolverVolunteer(
                UUID.randomUUID(), "Bob",
                List.of(new SkillLevel("Java", "Advanced")),
                2, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );
        SolverTask t2 = new SolverTask(
                UUID.randomUUID(), "Task 2", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        AssignedPair pair1 = new AssignedPair(v1.id(), t1.id(), 1, feasibleFit(85.0));
        AssignedPair pairDuplicateVol = new AssignedPair(v1.id(), t2.id(), 1, feasibleFit(85.0));
        SolverResult rDuplicateVol = new SolverResult(List.of(pair1, pairDuplicateVol), List.of(), 0L);

        List<String> vDup = PlanValidator.validate(List.of(v1, v2), List.of(t1, t2), DEFAULT_CONFIG, rDuplicateVol);
        assertFalse(vDup.isEmpty());
        assertEquals(1, vDup.size());
        assertEquals("Volunteer assigned multiple times: " + v1.id(), vDup.get(0));
    }

    @Test
    @DisplayName("f2) Hand-built violation: task exceeds headcount")
    void overHeadcount() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverVolunteer v2 = new SolverVolunteer(
                UUID.randomUUID(), "Bob",
                List.of(new SkillLevel("Java", "Advanced")),
                2, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        AssignedPair pair1 = new AssignedPair(v1.id(), t1.id(), 1, feasibleFit(85.0));
        AssignedPair pair2 = new AssignedPair(v2.id(), t1.id(), 2, feasibleFit(85.0));
        SolverResult rOverHeadcount = new SolverResult(List.of(pair1, pair2), List.of(), 0L);
        List<String> vHeadcount = PlanValidator.validate(List.of(v1, v2), List.of(t1), DEFAULT_CONFIG, rOverHeadcount);
        assertEquals(List.of(
                "Slot number out of range for task: " + t1.id(),
                "Task exceeds headcount: " + t1.id()
        ), vHeadcount);
    }

    @Test
    @DisplayName("f3) Hand-built violation: volunteer missing mandatory skill")
    void missingMandatorySkill() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer vNoJava = new SolverVolunteer(
                UUID.randomUUID(), "NoJava",
                List.of(new SkillLevel("Lighting", "Advanced")),
                3, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        AssignedPair pairMissingSkill = new AssignedPair(vNoJava.id(), t1.id(), 1, feasibleFit(85.0));
        SolverResult rMissingSkill = new SolverResult(List.of(pairMissingSkill), List.of(), 0L);
        List<String> vSkill = PlanValidator.validate(List.of(vNoJava), List.of(t1), DEFAULT_CONFIG, rMissingSkill);
        assertFalse(vSkill.isEmpty());
        assertEquals(1, vSkill.size());
        assertEquals("Volunteer missing mandatory skill for task: " + t1.id(), vSkill.get(0));
    }

    @Test
    @DisplayName("f4) Hand-built violation: volunteer unavailable")
    void unavailable() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer vUnavailable = new SolverVolunteer(
                UUID.randomUUID(), "Unavailable",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.0,
                List.of(new AvailabilitySlot("Tuesday", "Morning")), // Wrong day!
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        AssignedPair pairUnavailable = new AssignedPair(vUnavailable.id(), t1.id(), 1, feasibleFit(85.0));
        SolverResult rUnavailable = new SolverResult(List.of(pairUnavailable), List.of(), 0L);
        List<String> vAvail = PlanValidator.validate(List.of(vUnavailable), List.of(t1), DEFAULT_CONFIG, rUnavailable);
        assertFalse(vAvail.isEmpty());
        assertEquals(1, vAvail.size());
        assertEquals("Volunteer unavailable for task: " + t1.id(), vAvail.get(0));
    }

    @Test
    @DisplayName("f5) Hand-built violation: volunteer over hours")
    void overHours() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0); // 2 hours

        SolverVolunteer vOverHours = new SolverVolunteer(
                UUID.randomUUID(), "OverHours",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                1, List.of() // maxHours 1, but task is 2 hours!
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        AssignedPair pairOverHours = new AssignedPair(vOverHours.id(), t1.id(), 1, feasibleFit(85.0));
        SolverResult rOverHours = new SolverResult(List.of(pairOverHours), List.of(), 0L);
        List<String> vHours = PlanValidator.validate(List.of(vOverHours), List.of(t1), DEFAULT_CONFIG, rOverHours);
        assertFalse(vHours.isEmpty());
        assertEquals(1, vHours.size());
        assertEquals("Volunteer over hours for task: " + t1.id(), vHours.get(0));
    }

    @Test
    @DisplayName("f6) Hand-built violation: unknown volunteer ID")
    void unknownVolunteer() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        UUID unknownId = UUID.randomUUID();
        AssignedPair pairUnknown = new AssignedPair(unknownId, t1.id(), 1, feasibleFit(85.0));
        SolverResult rUnknown = new SolverResult(List.of(pairUnknown), List.of(), 0L);
        List<String> vUnk = PlanValidator.validate(List.of(v1), List.of(t1), DEFAULT_CONFIG, rUnknown);
        assertFalse(vUnk.isEmpty());
        assertEquals(1, vUnk.size());
        assertEquals("Unknown volunteer ID: " + unknownId, vUnk.get(0));
    }

    @Test
    @DisplayName("f7) Hand-built violation: headcount mismatch")
    void headcountMismatch() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        AssignedPair pairValid = new AssignedPair(v1.id(), t1.id(), 1, feasibleFit(85.0));
        SolverResult rMismatch = new SolverResult(List.of(pairValid), List.of(new UnfilledTask(t1.id(), 1, null)), 0L);
        List<String> vMismatch = PlanValidator.validate(List.of(v1), List.of(t1), DEFAULT_CONFIG, rMismatch);
        assertFalse(vMismatch.isEmpty());
        assertEquals(1, vMismatch.size());
        assertEquals("Headcount mismatch for task: " + t1.id(), vMismatch.get(0));
    }

    @Test
    @DisplayName("f8) Hand-built violation: duplicate slot number")
    void duplicateSlotNumber() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverVolunteer v2 = new SolverVolunteer(
                UUID.randomUUID(), "Bob",
                List.of(new SkillLevel("Java", "Advanced")),
                2, 4.0,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask tDouble = new SolverTask(
                UUID.randomUUID(), "Task Double", monday, start, end, 2,
                List.of("Java"), List.of(), false
        );

        AssignedPair pairDupSlot1 = new AssignedPair(v1.id(), tDouble.id(), 1, feasibleFit(85.0));
        AssignedPair pairDupSlot2 = new AssignedPair(v2.id(), tDouble.id(), 1, feasibleFit(85.0));
        SolverResult rDupSlot = new SolverResult(List.of(pairDupSlot1, pairDupSlot2), List.of(), 0L);
        List<String> vDupSlot = PlanValidator.validate(List.of(v1, v2), List.of(tDouble), DEFAULT_CONFIG, rDupSlot);
        assertFalse(vDupSlot.isEmpty());
        assertEquals(1, vDupSlot.size());
        assertEquals("Duplicate slot number for task: " + tDouble.id(), vDupSlot.get(0));
    }

    @Test
    @DisplayName("f9) Hand-built violation: unknown task ID")
    void unknownTask() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        UUID unknownTaskId = UUID.randomUUID();
        AssignedPair pairUnknownTask = new AssignedPair(v1.id(), unknownTaskId, 1, feasibleFit(85.0));
        SolverResult rUnknownTask = new SolverResult(List.of(pairUnknownTask), List.of(new UnfilledTask(t1.id(), 1, null)), 0L);
        List<String> vUnkTask = PlanValidator.validate(List.of(v1), List.of(t1), DEFAULT_CONFIG, rUnknownTask);
        assertFalse(vUnkTask.isEmpty());
        assertEquals(1, vUnkTask.size());
        assertEquals("Unknown task ID: " + unknownTaskId, vUnkTask.get(0));
    }

    @Test
    @DisplayName("f10) Hand-built violation: duplicate unfilled entry")
    void duplicateUnfilledEntry() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverTask tDouble = new SolverTask(
                UUID.randomUUID(), "Task Double", monday, start, end, 2,
                List.of("Java"), List.of(), false
        );

        UnfilledTask ut1 = new UnfilledTask(tDouble.id(), 1, null);
        UnfilledTask ut2 = new UnfilledTask(tDouble.id(), 1, null);
        SolverResult rDupUnfilled = new SolverResult(List.of(), List.of(ut1, ut2), 0L);
        List<String> vDupUnfilled = PlanValidator.validate(List.of(), List.of(tDouble), DEFAULT_CONFIG, rDupUnfilled);
        assertFalse(vDupUnfilled.isEmpty());
        assertEquals(1, vDupUnfilled.size());
        assertEquals("Duplicate unfilled entry for task: " + tDouble.id(), vDupUnfilled.get(0));
    }

    @Test
    @DisplayName("f11) Hand-built violation: null config")
    void nullConfig() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(11, 0);

        SolverVolunteer v1 = new SolverVolunteer(
                UUID.randomUUID(), "Alice",
                List.of(new SkillLevel("Java", "Advanced")),
                3, 4.5,
                List.of(new AvailabilitySlot("Monday", "Morning")),
                8, List.of()
        );
        SolverTask t1 = new SolverTask(
                UUID.randomUUID(), "Task 1", monday, start, end, 1,
                List.of("Java"), List.of(), false
        );

        SolverResult rNullConfig = new SolverResult(List.of(), List.of(new UnfilledTask(t1.id(), 1, null)), 0L);
        List<String> vNullConfig = PlanValidator.validate(List.of(v1), List.of(t1), null, rNullConfig);
        assertFalse(vNullConfig.isEmpty());
        assertEquals(1, vNullConfig.size());
        assertEquals("Config cannot be null", vNullConfig.get(0));
    }

    private static FitResult feasibleFit(double fit) {
        return new FitResult(true, null, fit, new FitBreakdown(0.5, 0.5, 0.5, 1.0, 0.0), List.of());
    }

    private static SolverVolunteer generateRandomVolunteer(Random rng, String name) {
        int numSkills = 1 + rng.nextInt(3);
        List<SkillLevel> skills = new ArrayList<>();
        for (int s = 0; s < numSkills; s++) {
            String skill = POSSIBLE_SKILLS[rng.nextInt(POSSIBLE_SKILLS.length)];
            String level = ModelValues.SKILL_LEVELS.get(rng.nextInt(ModelValues.SKILL_LEVELS.size()));
            skills.add(new SkillLevel(skill, level));
        }

        int pastEvents = rng.nextInt(10);
        Double rating = rng.nextBoolean() ? null : (1.0 + 4.0 * rng.nextDouble());

        int numSlots = 1 + rng.nextInt(6);
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
