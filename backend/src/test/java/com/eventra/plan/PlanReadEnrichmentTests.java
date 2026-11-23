package com.eventra.plan;

import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.model.SkillLevel;
import com.eventra.plan.dto.PlanResponse;
import com.eventra.plan.dto.SkillGapDto;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
class PlanReadEnrichmentTests {

    @Autowired
    private PlanService planService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            Optional<Plan> planOpt = planRepository.findByEventId(eventId);
            if (planOpt.isPresent()) {
                jdbcTemplate.update("DELETE FROM assignments WHERE plan_id = ?", planOpt.get().getId());
                jdbcTemplate.update("DELETE FROM plans WHERE event_id = ?", eventId);
            }
            jdbcTemplate.update("DELETE FROM tasks WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    private Event createEvent(String name) {
        Event event = new Event();
        event.setName(name);
        event.setDescription("Enrichment test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private Task createTask(Event event, String title, LocalDate date, LocalTime start, LocalTime end, int headcount, List<String> mandatorySkills) {
        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle(title);
        task.setTaskDate(date);
        task.setStartTime(start);
        task.setEndTime(end);
        task.setHeadcount(headcount);
        task.setMandatorySkills(mandatorySkills != null ? mandatorySkills : List.of());
        return taskRepository.save(task);
    }

    private Volunteer createVolunteer(Event event, String name, String email, List<SkillLevel> skills) {
        Volunteer volunteer = new Volunteer();
        volunteer.setEventId(event.getId());
        volunteer.setFullName(name);
        volunteer.setEmail(email);
        volunteer.setSkills(skills != null ? skills : List.of());
        return volunteerRepository.save(volunteer);
    }

    private Assignment createAssignment(Plan plan, Task task, Volunteer vol, int slotNumber) {
        Assignment asg = new Assignment();
        asg.setPlan(plan);
        asg.setTask(task);
        asg.setVolunteer(vol);
        asg.setSlotNumber(slotNumber);
        asg.setFit(BigDecimal.valueOf(90.0));
        asg.setSkillScore(BigDecimal.valueOf(90.0));
        asg.setExperienceScore(BigDecimal.valueOf(80.0));
        asg.setPerformanceScore(BigDecimal.valueOf(80.0));
        asg.setLoadScore(BigDecimal.valueOf(80.0));
        asg.setInterestScore(BigDecimal.valueOf(80.0));
        asg.setReason("Test assignment");
        asg.setFlags(List.of());
        asg.setLocked(false);
        return assignmentRepository.save(asg);
    }

    @Test
    @DisplayName("a. skillNobodyHoldsGivesGapWithMissingSkill")
    void skillNobodyHoldsGivesGapWithMissingSkill() {
        Event event = createEvent("Skill Gap Event");
        Task task = createTask(event, "Emergency Desk", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(12, 0), 2, List.of("First Aid"));
        createVolunteer(event, "Bob Volunteer", "bob_gap@acropolis.in", List.of(new SkillLevel("Audio", "Intermediate")));

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals(1, response.skillGaps().size());
        SkillGapDto gap = response.skillGaps().get(0);
        assertEquals(task.getId(), gap.taskId());
        assertEquals("Emergency Desk", gap.taskTitle());
        assertEquals(2, gap.slotsUnfilled());
        assertEquals("First Aid", gap.missingSkill());
        assertEquals(1, response.summary().skillGapCount());
    }

    @Test
    @DisplayName("b. skillHeldButNobodyFreeGivesGapWithNullMissingSkill")
    void skillHeldButNobodyFreeGivesGapWithNullMissingSkill() {
        Event event = createEvent("Skill Held Event");
        Task task = createTask(event, "Medical Tent", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(12, 0), 2, List.of("First Aid"));
        createVolunteer(event, "Doctor Dan", "dan@acropolis.in", List.of(new SkillLevel("First Aid", "Advanced")));

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals(1, response.skillGaps().size());
        SkillGapDto gap = response.skillGaps().get(0);
        assertEquals(task.getId(), gap.taskId());
        assertEquals("Medical Tent", gap.taskTitle());
        assertEquals(2, gap.slotsUnfilled());
        assertNull(gap.missingSkill());
        assertEquals(0, response.summary().skillGapCount());
    }

    @Test
    @DisplayName("c. skillGapCountCountsOnlyNonNullMissingSkill")
    void skillGapCountCountsOnlyNonNullMissingSkill() {
        Event event = createEvent("Count Only Non-Null Event");

        Task task1 = createTask(event, "Alpha Electronics", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of("Electronics"));
        Task task2 = createTask(event, "Beta Audio", LocalDate.of(2026, 11, 25), LocalTime.of(14, 0), LocalTime.of(16, 0), 1, List.of("Audio"));

        createVolunteer(event, "Audio Tech", "audiotech@acropolis.in", List.of(new SkillLevel("Audio", "Advanced")));

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals(2, response.skillGaps().size());
        assertEquals(1, response.summary().skillGapCount());
    }

    @Test
    @DisplayName("skillGapsSortedByDateThenStartThenTitle")
    void skillGapsSortedByDateThenStartThenTitle() {
        Event event = createEvent("Sort Order Event");
        LocalDate date = LocalDate.of(2026, 11, 25);

        // 3 tasks: same date, different start times, and one pair with the same start and different titles
        Task taskB = createTask(event, "Bravo Desk", date, LocalTime.of(14, 0), LocalTime.of(16, 0), 1, List.of());
        Task taskA = createTask(event, "Zebra Setup", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of());
        Task taskC = createTask(event, "Charlie Desk", date, LocalTime.of(14, 0), LocalTime.of(16, 0), 1, List.of());

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals(3, response.skillGaps().size());
        assertEquals(taskA.getId(), response.skillGaps().get(0).taskId());
        assertEquals(taskB.getId(), response.skillGaps().get(1).taskId());
        assertEquals(taskC.getId(), response.skillGaps().get(2).taskId());
    }

    @Test
    @DisplayName("d. fullyFilledTaskHasNoGap")
    void fullyFilledTaskHasNoGap() {
        Event event = createEvent("Fully Filled Event");
        Task task = createTask(event, "Stage Hands", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(11, 0), 2, List.of("Audio"));

        Volunteer v1 = createVolunteer(event, "Alice", "alice_full@acropolis.in", List.of());
        Volunteer v2 = createVolunteer(event, "Bob", "bob_full@acropolis.in", List.of());

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan = planRepository.save(plan);

        createAssignment(plan, task, v1, 1);
        createAssignment(plan, task, v2, 2);

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals(2, response.summary().headcountFilled());
        assertEquals(2, response.summary().headcountRequired());
        assertTrue(response.skillGaps().isEmpty());
        assertEquals(0, response.summary().skillGapCount());
    }

    @Test
    @DisplayName("e. workloadSpreadExactStringForHandComputedCase")
    void workloadSpreadExactStringForHandComputedCase() {
        Event event = createEvent("Workload Spread Hand Computed Event");

        // Hand calculation arithmetic:
        // 2 distinct assigned volunteers (Alice and Bob).
        // 2 assignments across 2 tasks:
        // - Task 1: 09:00 to 13:00 = 4.0 hours (assigned to Alice)
        // - Task 2: 10:00 to 15:00 = 5.0 hours (assigned to Bob)
        // Total task hours = 4.0 + 5.0 = 9.0 hours.
        // Distinct assigned volunteers = 2.
        // Average task hours per volunteer = 9.0 / 2 = 4.5 hours.
        // Formatted exact string = "4.5 hrs / vol".

        Task task1 = createTask(event, "Task 1 (4h)", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(13, 0), 1, List.of());
        Task task2 = createTask(event, "Task 2 (5h)", LocalDate.of(2026, 11, 25), LocalTime.of(10, 0), LocalTime.of(15, 0), 1, List.of());

        Volunteer alice = createVolunteer(event, "Alice Worker", "alice_work@acropolis.in", List.of());
        Volunteer bob = createVolunteer(event, "Bob Worker", "bob_work@acropolis.in", List.of());

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan = planRepository.save(plan);

        createAssignment(plan, task1, alice, 1);
        createAssignment(plan, task2, bob, 1);

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals("4.5 hrs / vol", response.summary().workloadSpread());
    }

    @Test
    @DisplayName("f. workloadSpreadIsDashesWhenNoAssignments")
    void workloadSpreadIsDashesWhenNoAssignments() {
        Event event = createEvent("No Assignments Event");
        createTask(event, "Solo Task", LocalDate.of(2026, 11, 25), LocalTime.of(9, 0), LocalTime.of(12, 0), 2, List.of());

        PlanResponse response = planService.getPlan(event.getId());

        assertEquals("--", response.summary().workloadSpread());
    }
}
