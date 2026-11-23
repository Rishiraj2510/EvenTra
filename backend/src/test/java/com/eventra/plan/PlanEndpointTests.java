package com.eventra.plan;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class PlanEndpointTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";
    private static final String COOKIE_NAME = "eventra_token";

    @Autowired
    private MockMvc mockMvc;

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
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User organiser;
    private User volunteerUser;
    private String organiserToken;
    private String volunteerToken;

    @BeforeEach
    void setUp() {
        cleanData();

        organiser = new User();
        organiser.setEmail("organiser_plan_test@acropolis.in");
        organiser.setFullName("Organiser Plan Tester");
        organiser.setPasswordHash(passwordEncoder.encode("OrganiserPass123!"));
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());

        volunteerUser = new User();
        volunteerUser.setEmail("volunteer_plan_test@acropolis.in");
        volunteerUser.setFullName("Volunteer Plan Tester");
        volunteerUser.setPasswordHash(passwordEncoder.encode("VolunteerPass123!"));
        volunteerUser.setRole("volunteer");
        volunteerUser = userRepository.save(volunteerUser);
        volunteerToken = jwtService.generateToken(volunteerUser.getId(), volunteerUser.getRole(), volunteerUser.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        cleanData();
    }

    private void cleanData() {
        assignmentRepository.deleteAll();
        planRepository.deleteAll();
        taskRepository.deleteAll();
        volunteerRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Event createEvent(String name) {
        Event event = new Event();
        event.setName(name);
        event.setDescription("Plan test event description");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        return eventRepository.save(event);
    }

    private Task createTask(Event event, String title, LocalDate date, LocalTime start, LocalTime end, int headcount) {
        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle(title);
        task.setTaskDate(date);
        task.setStartTime(start);
        task.setEndTime(end);
        task.setHeadcount(headcount);
        return taskRepository.save(task);
    }

    private Volunteer createVolunteer(Event event, String name, String email) {
        Volunteer volunteer = new Volunteer();
        volunteer.setEventId(event.getId());
        volunteer.setFullName(name);
        volunteer.setEmail(email);
        return volunteerRepository.save(volunteer);
    }

    private Assignment createAssignment(Plan plan, Task task, Volunteer vol, int slotNumber,
                                        double fit, double skill, double exp, double perf, double load, double interest,
                                        String reason, List<String> flags, boolean locked) {
        Assignment asg = new Assignment();
        asg.setPlan(plan);
        asg.setTask(task);
        asg.setVolunteer(vol);
        asg.setSlotNumber(slotNumber);
        asg.setFit(BigDecimal.valueOf(fit));
        asg.setSkillScore(BigDecimal.valueOf(skill));
        asg.setExperienceScore(BigDecimal.valueOf(exp));
        asg.setPerformanceScore(BigDecimal.valueOf(perf));
        asg.setLoadScore(BigDecimal.valueOf(load));
        asg.setInterestScore(BigDecimal.valueOf(interest));
        asg.setReason(reason);
        asg.setFlags(flags);
        asg.setLocked(locked);
        return assignmentRepository.save(asg);
    }

    // -------------------------------------------------------------------------
    // Endpoint Security & Basic Status Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: unauthenticated returns 401")
    void unauthenticatedReturns401() throws Exception {
        UUID eventId = UUID.randomUUID();
        mockMvc.perform(get("/api/events/" + eventId + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: volunteer role returns 403")
    void volunteerRoleReturns403() throws Exception {
        Event event = createEvent("Test Event");
        mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, volunteerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: unknown event id returns 404 with error Event not found")
    void unknownEventIdReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(get("/api/events/" + unknownId + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Event not found", json.path("error").asText());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: event with no plan returns status none, empty assignments, zeroed summary")
    void eventWithNoPlanReturnsStatusNone() throws Exception {
        Event event = createEvent("No Plan Event");
        createTask(event, "Task A", LocalDate.of(2026, 11, 25), LocalTime.of(10, 0), LocalTime.of(12, 0), 3);
        createTask(event, "Task B", LocalDate.of(2026, 11, 25), LocalTime.of(14, 0), LocalTime.of(16, 0), 2);

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(event.getId().toString(), json.path("eventId").asText());
        assertEquals(PlanStatus.NONE, json.path("status").asText());
        assertFalse(json.path("stale").asBoolean());
        assertTrue(json.path("assignments").isEmpty());
        assertEquals(2, json.path("skillGaps").size());

        JsonNode summary = json.path("summary");
        assertEquals(0.0, summary.path("averageFit").asDouble(), 1e-9);
        assertEquals(0, summary.path("unqualifiedAssignments").asLong());
        assertEquals("--", summary.path("workloadSpread").asText());
        assertEquals(0, summary.path("headcountFilled").asLong());
        assertEquals(5, summary.path("headcountRequired").asLong()); // 3 + 2 = 5
        assertEquals(0, summary.path("skillGapCount").asLong());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: draft plan with 3 assignments across 2 tasks computes exact summary and order")
    void draftPlanWith3AssignmentsComputesExactSummaryAndOrder() throws Exception {
        Event event = createEvent("Festival Event");

        // Alpha Audio: 2026-11-26 09:00 (Morning), headcount 3
        Task alphaAudio = createTask(event, "Alpha Audio", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(11, 0), 3);
        // Beta Logistics: 2026-11-26 14:00 (Afternoon), headcount 2
        Task betaLogistics = createTask(event, "Beta Logistics", LocalDate.of(2026, 11, 26), LocalTime.of(14, 0), LocalTime.of(16, 0), 2);

        Volunteer v1 = createVolunteer(event, "Alice Singer", "alice@acropolis.in");
        Volunteer v2 = createVolunteer(event, "Bob Builder", "bob@acropolis.in");
        Volunteer v3 = createVolunteer(event, "Charlie Chaplin", "charlie@acropolis.in");

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan = planRepository.save(plan);

        // Assignment 1: Alpha Audio (slot 1) -> Alice
        // fit: 92.4, skill 95.0, exp 90.0, perf 85.0, load 80.0, interest 90.0
        createAssignment(plan, alphaAudio, v1, 1, 92.4, 95.0, 90.0, 85.0, 80.0, 90.0, "Audio lead", List.of(), false);

        // Assignment 2: Alpha Audio (slot 2) -> Bob
        // fit: 81.6, skill 80.0, exp 70.0, perf 85.0, load 90.0, interest 80.0
        createAssignment(plan, alphaAudio, v2, 2, 81.6, 80.0, 70.0, 85.0, 90.0, 80.0, "Audio assistant", List.of(), false);

        // Assignment 3: Beta Logistics (slot 1) -> Charlie (Each volunteer has 1 assignment)
        // fit: 74.5, skill 70.0, exp 60.0, perf 75.0, load 80.0, interest 70.0
        createAssignment(plan, betaLogistics, v3, 1, 74.5, 70.0, 60.0, 75.0, 80.0, 70.0, "Logistics help", List.of(), true);

        // Hand-computed summary:
        // - headcountFilled = 3
        // - headcountRequired = 3 + 2 = 5
        // - averageFit = (92.4 + 81.6 + 74.5) / 3 = 248.5 / 3 = 82.8333... -> rounded to 1 decimal = 82.8
        // - workloadSpread = total hours (2.0 + 2.0 + 2.0 = 6.0) / 3 distinct volunteers = 2.0 hrs / vol
        // - unqualifiedAssignments = 0
        // - skillGapCount = 0

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.DRAFT, json.path("status").asText());
        assertFalse(json.path("stale").asBoolean());

        JsonNode summary = json.path("summary");
        assertEquals(82.8, summary.path("averageFit").asDouble(), 1e-9);
        assertEquals(0, summary.path("unqualifiedAssignments").asLong());
        assertEquals("2.0 hrs / vol", summary.path("workloadSpread").asText());
        assertEquals(3, summary.path("headcountFilled").asLong());
        assertEquals(5, summary.path("headcountRequired").asLong());
        assertEquals(0, summary.path("skillGapCount").asLong());

        JsonNode assignments = json.path("assignments");
        assertEquals(3, assignments.size());

        // Sort order check: Alpha Audio (Morning 09:00) before Beta Logistics (Afternoon 14:00)
        // Item 0: Alpha Audio slot 1 (Alice)
        assertEquals("Alpha Audio", assignments.get(0).path("taskTitle").asText());
        assertEquals("Alice Singer", assignments.get(0).path("volunteerName").asText());
        assertEquals(92.4, assignments.get(0).path("fit").asDouble(), 1e-9);
        // Assert all five breakdown factors of assignment 0 (0.95, 0.90, 0.85, 0.80, 0.90)
        assertEquals(0.95, assignments.get(0).path("breakdown").path("skill").asDouble(), 1e-9);
        assertEquals(0.90, assignments.get(0).path("breakdown").path("experience").asDouble(), 1e-9);
        assertEquals(0.85, assignments.get(0).path("breakdown").path("performance").asDouble(), 1e-9);
        assertEquals(0.80, assignments.get(0).path("breakdown").path("load").asDouble(), 1e-9);
        assertEquals(0.90, assignments.get(0).path("breakdown").path("interest").asDouble(), 1e-9);
        assertFalse(assignments.get(0).path("locked").asBoolean());

        // Item 1: Alpha Audio slot 2 (Bob)
        assertEquals("Alpha Audio", assignments.get(1).path("taskTitle").asText());
        assertEquals("Bob Builder", assignments.get(1).path("volunteerName").asText());
        assertEquals(81.6, assignments.get(1).path("fit").asDouble(), 1e-9);

        // Item 2: Beta Logistics slot 1 (Charlie)
        assertEquals("Beta Logistics", assignments.get(2).path("taskTitle").asText());
        assertEquals("Charlie Chaplin", assignments.get(2).path("volunteerName").asText());
        assertEquals(74.5, assignments.get(2).path("fit").asDouble(), 1e-9);
        assertTrue(assignments.get(2).path("locked").asBoolean());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: status value draft returned correctly")
    void statusDraftReturnedCorrectly() throws Exception {
        Event event = createEvent("Event Draft");
        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        planRepository.save(plan);

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.DRAFT, json.path("status").asText());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: status value approved returned correctly")
    void statusApprovedReturnedCorrectly() throws Exception {
        Event event = createEvent("Event Approved");
        Plan plan = new Plan(event.getId(), PlanStatus.APPROVED);
        planRepository.save(plan);

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.APPROVED, json.path("status").asText());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: status value published returned correctly")
    void statusPublishedReturnedCorrectly() throws Exception {
        Event event = createEvent("Event Published");
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        planRepository.save(plan);

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.PUBLISHED, json.path("status").asText());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: stale=true is returned")
    void staleTrueReturned() throws Exception {
        Event event = createEvent("Stale Event");
        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(true);
        planRepository.save(plan);

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.path("stale").asBoolean());
    }

    @Test
    @DisplayName("GET /api/events/{eventId}/plan: flags containing unqualified counted in unqualifiedAssignments")
    void flagsContainingUnqualifiedCounted() throws Exception {
        Event event = createEvent("Unqualified Event");
        Task task = createTask(event, "General Task", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(11, 0), 2);
        Volunteer v1 = createVolunteer(event, "Vol 1", "v1@acropolis.in");
        Volunteer v2 = createVolunteer(event, "Vol 2", "v2@acropolis.in");

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        planRepository.save(plan);

        createAssignment(plan, task, v1, 1, 60.0, 50.0, 50.0, 50.0, 50.0, 50.0, "Reason 1", List.of("unqualified", "First-timer"), false);
        createAssignment(plan, task, v2, 2, 85.0, 90.0, 80.0, 80.0, 80.0, 80.0, "Reason 2", List.of(), false);

        MvcResult result = mockMvc.perform(get("/api/events/" + event.getId() + "/plan")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, json.path("summary").path("unqualifiedAssignments").asLong());
    }

    // -------------------------------------------------------------------------
    // Database Constraints Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DB constraint: second plan for the same event rejected")
    void secondPlanForSameEventRejected() {
        Event event = createEvent("Unique Event");
        Plan plan1 = new Plan(event.getId(), PlanStatus.DRAFT);
        planRepository.saveAndFlush(plan1);

        Plan plan2 = new Plan(event.getId(), PlanStatus.DRAFT);
        assertThrows(DataIntegrityViolationException.class, () -> {
            planRepository.saveAndFlush(plan2);
        });
    }

    @Test
    @DisplayName("DB constraint: same volunteer twice in a plan rejected")
    void sameVolunteerTwiceInPlanRejected() {
        Event event = createEvent("Vol Plan Event");
        Task task1 = createTask(event, "Task 1", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Task task2 = createTask(event, "Task 2", LocalDate.of(2026, 11, 26), LocalTime.of(10, 0), LocalTime.of(11, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");

        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        createAssignment(plan, task1, vol, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Reason 1", List.of(), false);
        assignmentRepository.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            createAssignment(plan, task2, vol, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Reason 2", List.of(), false);
            assignmentRepository.flush();
        });
    }

    @Test
    @DisplayName("DB constraint: same (task, slot_number) twice in a plan rejected")
    void sameTaskSlotNumberTwiceInPlanRejected() {
        Event event = createEvent("Slot Plan Event");
        Task task = createTask(event, "Task 1", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 2);
        Volunteer v1 = createVolunteer(event, "Vol 1", "v1@acropolis.in");
        Volunteer v2 = createVolunteer(event, "Vol 2", "v2@acropolis.in");

        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        createAssignment(plan, task, v1, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Reason 1", List.of(), false);
        assignmentRepository.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            createAssignment(plan, task, v2, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Reason 2", List.of(), false);
            assignmentRepository.flush();
        });
    }

    @Test
    @DisplayName("DB constraint: invalid plan status rejected")
    void invalidPlanStatusRejected() {
        Event event = createEvent("Invalid Status Event");
        Plan plan = new Plan(event.getId(), "invalid_status");
        assertThrows(DataIntegrityViolationException.class, () -> {
            planRepository.saveAndFlush(plan);
        });
    }

    @Test
    @DisplayName("DB constraint: fit 101 rejected")
    void fit101Rejected() {
        Event event = createEvent("Fit 101 Event");
        Task task = createTask(event, "Task 1", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        assertThrows(DataIntegrityViolationException.class, () -> {
            createAssignment(plan, task, vol, 1, 101.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Over fit", List.of(), false);
            assignmentRepository.flush();
        });
    }

    @Test
    @DisplayName("DB constraint: fit -1 rejected")
    void fitMinus1Rejected() {
        Event event = createEvent("Fit -1 Event");
        Task task = createTask(event, "Task 1", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        assertThrows(DataIntegrityViolationException.class, () -> {
            createAssignment(plan, task, vol, 1, -1.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Under fit", List.of(), false);
            assignmentRepository.flush();
        });
    }

    @Test
    @DisplayName("DB constraint: slot_number 0 rejected")
    void slotNumber0Rejected() {
        Event event = createEvent("Slot 0 Event");
        Task task = createTask(event, "Task 1", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        assertThrows(DataIntegrityViolationException.class, () -> {
            createAssignment(plan, task, vol, 0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Slot 0", List.of(), false);
            assignmentRepository.flush();
        });
    }

    @Test
    @DisplayName("Schema check: assignments foreign keys on task_id and volunteer_id are NOT deferrable")
    void assignmentsForeignKeysNotDeferrable() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT conname, condeferrable FROM pg_constraint " +
                "WHERE conname IN ('assignments_task_id_fkey', 'assignments_volunteer_id_fkey')"
        );
        assertEquals(2, rows.size(), "Both assignments FK constraints must exist");
        for (Map<String, Object> row : rows) {
            String conname = (String) row.get("conname");
            Boolean condeferrable = (Boolean) row.get("condeferrable");
            assertNotNull(condeferrable, "condeferrable must not be null for " + conname);
            assertFalse(condeferrable, conname + " must NOT be deferrable (plain NO ACTION)");
        }
    }

    // -------------------------------------------------------------------------
    // Foreign Key & Delete Behavior Tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Delete behavior: deleteEventWithPlanAndAssignmentsReturns409")
    void deleteEventWithPlanAndAssignmentsReturns409() throws Exception {
        Event event = createEvent("Delete 409 Event");
        Task task = createTask(event, "Task", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));
        createAssignment(plan, task, vol, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Delete test", List.of(), false);
        assignmentRepository.flush();

        // DELETE /api/events/{id} as organiser (cookie + X-Requested-With: EvenTra)
        mockMvc.perform(delete("/api/events/" + event.getId())
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict());

        // Assert plan and assignment rows still exist
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM plans WHERE id = ?", Integer.class, plan.getId()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM assignments WHERE plan_id = ?", Integer.class, plan.getId()));
    }

    @Test
    @DisplayName("Delete behavior: deleteEventWithEmptyPlanSucceedsAndRemovesPlan")
    void deleteEventWithEmptyPlanSucceedsAndRemovesPlan() throws Exception {
        Event event = createEvent("Empty Plan Event");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        // DELETE /api/events/{id} on event with a plan but NO tasks, volunteers or assignments
        mockMvc.perform(delete("/api/events/" + event.getId())
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isNoContent());

        // Assert the plans row is gone
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM plans WHERE id = ?", Integer.class, plan.getId()));
    }

    @Test
    @DisplayName("Delete behavior: rawEventDeleteWithAssignmentsIsRejectedByDatabase")
    void rawEventDeleteWithAssignmentsIsRejectedByDatabase() {
        Event event = createEvent("Raw Delete Cascade Event");
        Task task = createTask(event, "Task", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));
        createAssignment(plan, task, vol, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Cascade test", List.of(), false);
        assignmentRepository.flush();

        // documents that the DB itself refuses; the service refuses first.
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("delete from events where id = ?", event.getId());
        });
    }

    @Test
    @DisplayName("Delete behavior: deleting a task row that has an assignment is rejected by DB")
    void deletingTaskWithAssignmentRejected() {
        Event event = createEvent("Task Delete Event");
        Task task = createTask(event, "Task", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));
        createAssignment(plan, task, vol, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Delete test", List.of(), false);
        assignmentRepository.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            taskRepository.deleteById(task.getId());
            taskRepository.flush();
        });
    }

    @Test
    @DisplayName("Delete behavior: deleting a volunteer row that has an assignment is rejected by DB")
    void deletingVolunteerWithAssignmentRejected() {
        Event event = createEvent("Vol Delete Event");
        Task task = createTask(event, "Task", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        Volunteer vol = createVolunteer(event, "Vol", "v@acropolis.in");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));
        createAssignment(plan, task, vol, 1, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, "Delete test", List.of(), false);
        assignmentRepository.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            volunteerRepository.deleteById(vol.getId());
            volunteerRepository.flush();
        });
    }
}
