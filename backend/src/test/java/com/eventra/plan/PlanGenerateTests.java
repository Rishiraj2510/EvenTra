package com.eventra.plan;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.scoring.ScoringService;
import com.eventra.scoring.model.FitResult;
import com.eventra.scoring.model.ScoreWeights;
import com.eventra.scoring.model.ScoringConfig;
import com.eventra.scoring.model.SolverTask;
import com.eventra.scoring.model.SolverVolunteer;
import com.eventra.settings.Settings;
import com.eventra.settings.SettingsRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class PlanGenerateTests {

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
    private SettingsRepository settingsRepository;

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
        organiser.setEmail("organiser_generate_test@acropolis.in");
        organiser.setFullName("Organiser Generate Tester");
        organiser.setPasswordHash(passwordEncoder.encode("OrganiserPass123!"));
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());

        volunteerUser = new User();
        volunteerUser.setEmail("volunteer_generate_test@acropolis.in");
        volunteerUser.setFullName("Volunteer Generate Tester");
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
        event.setDescription("Generate test event description");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        return eventRepository.save(event);
    }

    private Task createTask(Event event, String title, LocalDate date, LocalTime start, LocalTime end, int headcount,
                            List<String> mandatory, List<String> preferred, boolean critical) {
        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle(title);
        task.setTaskDate(date);
        task.setStartTime(start);
        task.setEndTime(end);
        task.setHeadcount(headcount);
        task.setMandatorySkills(mandatory != null ? mandatory : List.of());
        task.setPreferredSkills(preferred != null ? preferred : List.of());
        task.setCritical(critical);
        return taskRepository.save(task);
    }

    private Volunteer createVolunteer(Event event, String name, String email,
                                       List<SkillLevel> skills, List<AvailabilitySlot> avail,
                                       int pastEvents, Integer maxHours, List<String> interests) {
        Volunteer v = new Volunteer();
        v.setEventId(event.getId());
        v.setFullName(name);
        v.setEmail(email);
        v.setSkills(skills != null ? skills : List.of());
        v.setAvailability(avail != null ? avail : List.of());
        v.setPastEventsCount(pastEvents);
        v.setMaxHoursPerEvent(maxHours);
        v.setInterests(interests != null ? interests : List.of());
        return volunteerRepository.save(v);
    }

    // -------------------------------------------------------------------------
    // 1. Auth & CSRF Tests (4 methods)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: unauthenticated returns 401")
    void generateUnauthenticatedReturns401() throws Exception {
        UUID eventId = UUID.randomUUID();
        mockMvc.perform(post("/api/events/" + eventId + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("generate: volunteer role returns 403")
    void generateVolunteerReturns403() throws Exception {
        Event event = createEvent("403 Event");
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, volunteerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("generate: unknown event returns 404")
    void generateUnknownEventReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/events/" + unknownId + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Event not found", json.path("error").asText());
    }

    @Test
    @DisplayName("generate: missing X-Requested-With header rejected")
    void generateMissingCsrfHeaderRejected() throws Exception {
        Event event = createEvent("CSRF Event");
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 2. Empty Tasks or Volunteers (2 methods)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: no tasks returns 409 Nothing to assign")
    void generateNoTasksReturns409() throws Exception {
        Event event = createEvent("No Tasks Event");
        createVolunteer(event, "Vol 1", "v1@test.com", List.of(), List.of(), 1, 8, List.of());

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Nothing to assign", json.path("error").asText());
    }

    @Test
    @DisplayName("generate: no volunteers returns 409 Nothing to assign")
    void generateNoVolunteersReturns409() throws Exception {
        Event event = createEvent("No Volunteers Event");
        createTask(event, "Task 1", LocalDate.of(2026, 11, 26), LocalTime.of(9, 0), LocalTime.of(12, 0), 1, List.of(), List.of(), false);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Nothing to assign", json.path("error").asText());
    }

    // -------------------------------------------------------------------------
    // 3. Null Task Time Validation (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: task with null start time returns 409 and no plan row created")
    void generateTaskWithNullStartTimeReturns409() throws Exception {
        Event event = createEvent("Null Time Event");
        createVolunteer(event, "Vol 1", "v1@test.com", List.of(), List.of(), 1, 8, List.of());

        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle("Null Start Time Task");
        task.setTaskDate(LocalDate.of(2026, 11, 26));
        task.setStartTime(null); // null start time
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(1);
        taskRepository.save(task);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Every task needs a date, start time and end time", json.path("error").asText());

        Integer planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM plans WHERE event_id = ?", Integer.class, event.getId());
        assertEquals(0, planCount, "No plan row should be created when validation fails");
    }

    // -------------------------------------------------------------------------
    // 4. Small Case (2 methods: 200 draft + exact scores matching ScoringService)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: small case 1 task headcount 1, 1 volunteer -> 200, draft with 1 assignment")
    void generateSmallCaseProducesDraftWithOneAssignment() throws Exception {
        Event event = createEvent("Small Case Event");
        LocalDate date = LocalDate.of(2026, 11, 26); // Thursday
        Task task = createTask(event, "Stage Manager", date, LocalTime.of(9, 0), LocalTime.of(12, 0), 1,
                List.of("Audio"), List.of("Lighting"), false);

        Volunteer vol = createVolunteer(event, "Alice Singer", "alice@test.com",
                List.of(new SkillLevel("Audio", "Expert"), new SkillLevel("Lighting", "Beginner")),
                List.of(new AvailabilitySlot("Thursday", "Morning")),
                2, 8, List.of("Stage"));

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.DRAFT, json.path("status").asText());
        assertEquals(1, json.path("assignments").size());

        JsonNode asg = json.path("assignments").get(0);
        assertEquals(task.getId().toString(), asg.path("taskId").asText());
        assertEquals(vol.getId().toString(), asg.path("volunteerId").asText());
    }

    @Test
    @DisplayName("generate: stored fit and five scores equal ScoringService.score for that pair")
    void generateStoredScoresMatchScoringService() throws Exception {
        Event event = createEvent("Scores Event");
        LocalDate date = LocalDate.of(2026, 11, 26); // Thursday
        Task task = createTask(event, "Sound Engineer", date, LocalTime.of(9, 0), LocalTime.of(12, 0), 1,
                List.of("Audio"), List.of(), false);

        Volunteer vol = createVolunteer(event, "Bob Sound", "bob@test.com",
                List.of(new SkillLevel("Audio", "Intermediate")),
                List.of(new AvailabilitySlot("Thursday", "Morning")),
                3, 6, List.of("Sound"));

        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk());

        // Fetch stored assignment from DB
        List<Assignment> storedList = assignmentRepository.findAll();
        assertEquals(1, storedList.size());
        Assignment stored = storedList.get(0);

        // Compute expected via ScoringService
        Settings settings = settingsRepository.findById(true).orElseThrow();
        ScoringConfig config = new ScoringConfig(
                new ScoreWeights(settings.getWeightSkill(), settings.getWeightExperience(),
                        settings.getWeightPerformance(), settings.getWeightLoad(), settings.getWeightInterest()),
                settings.getFirstTimerBonus(),
                settings.getDefaultMaxHours()
        );

        SolverTask sTask = new SolverTask(task.getId(), task.getTitle(), task.getTaskDate(),
                task.getStartTime(), task.getEndTime(), task.getHeadcount(),
                task.getMandatorySkills(), task.getPreferredSkills(), task.isCritical());

        SolverVolunteer sVol = new SolverVolunteer(vol.getId(), vol.getFullName(),
                vol.getSkills(), vol.getPastEventsCount(), null,
                vol.getAvailability(), vol.getMaxHoursPerEvent(), vol.getInterests());

        FitResult expectedFit = ScoringService.score(sVol, sTask, config);
        assertTrue(expectedFit.feasible());

        BigDecimal expectedFitBd = BigDecimal.valueOf(expectedFit.fit()).setScale(3, RoundingMode.HALF_UP);
        BigDecimal expectedSkillBd = BigDecimal.valueOf(expectedFit.breakdown().skill() * 100.0).setScale(3, RoundingMode.HALF_UP);
        BigDecimal expectedExpBd = BigDecimal.valueOf(expectedFit.breakdown().experience() * 100.0).setScale(3, RoundingMode.HALF_UP);
        BigDecimal expectedPerfBd = BigDecimal.valueOf(expectedFit.breakdown().performance() * 100.0).setScale(3, RoundingMode.HALF_UP);
        BigDecimal expectedLoadBd = BigDecimal.valueOf(expectedFit.breakdown().load() * 100.0).setScale(3, RoundingMode.HALF_UP);
        BigDecimal expectedInterestBd = BigDecimal.valueOf(expectedFit.breakdown().interest() * 100.0).setScale(3, RoundingMode.HALF_UP);

        assertEquals(0, expectedFitBd.compareTo(stored.getFit()), "Stored fit must match ScoringService");
        assertEquals(0, expectedSkillBd.compareTo(stored.getSkillScore()), "Stored skillScore must match breakdown*100");
        assertEquals(0, expectedExpBd.compareTo(stored.getExperienceScore()), "Stored expScore must match breakdown*100");
        assertEquals(0, expectedPerfBd.compareTo(stored.getPerformanceScore()), "Stored perfScore must match breakdown*100");
        assertEquals(0, expectedLoadBd.compareTo(stored.getLoadScore()), "Stored loadScore must match breakdown*100");
        assertEquals(0, expectedInterestBd.compareTo(stored.getInterestScore()), "Stored interestScore must match breakdown*100");
    }

    // -------------------------------------------------------------------------
    // 5. Headcount 2 with 1 Volunteer (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: task headcount 2 with 1 volunteer produces 1 assignment without error")
    void generateTaskHeadcount2With1VolunteerProduces1Assignment() throws Exception {
        Event event = createEvent("Headcount 2 Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        createTask(event, "Logistics Double", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 2,
                List.of(), List.of(), false);

        createVolunteer(event, "Single Vol", "single@test.com",
                List.of(), List.of(new AvailabilitySlot("Thursday", "Morning")),
                0, 4, List.of());

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(1, json.path("assignments").size(), "Only 1 volunteer available, exactly 1 slot filled");
    }

    // -------------------------------------------------------------------------
    // 6. Volunteer & Task Multiplicity Bounds (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: no volunteer appears twice; no task has more assignments than headcount")
    void generateVolunteerAndTaskMultiplicityBoundsRespected() throws Exception {
        Event event = createEvent("Multiplicity Event");
        LocalDate date = LocalDate.of(2026, 11, 26);

        Task t1 = createTask(event, "Task A", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 2, List.of(), List.of(), false);
        Task t2 = createTask(event, "Task B", date, LocalTime.of(14, 0), LocalTime.of(16, 0), 1, List.of(), List.of(), false);

        for (int i = 1; i <= 5; i++) {
            createVolunteer(event, "Vol " + i, "vol" + i + "@test.com",
                    List.of(),
                    List.of(new AvailabilitySlot("Thursday", "Morning"), new AvailabilitySlot("Thursday", "Afternoon")),
                    1, 8, List.of());
        }

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode assignments = json.path("assignments");

        Set<String> seenVolunteers = new HashSet<>();
        Map<String, Integer> taskAssignmentCounts = new HashMap<>();

        for (int i = 0; i < assignments.size(); i++) {
            JsonNode asg = assignments.get(i);
            String vId = asg.path("volunteerId").asText();
            String tId = asg.path("taskId").asText();

            assertTrue(seenVolunteers.add(vId), "Volunteer " + vId + " must not appear more than once in assignments");
            taskAssignmentCounts.put(tId, taskAssignmentCounts.getOrDefault(tId, 0) + 1);
        }

        assertTrue(taskAssignmentCounts.getOrDefault(t1.getId().toString(), 0) <= t1.getHeadcount(),
                "Task A must not exceed headcount 2");
        assertTrue(taskAssignmentCounts.getOrDefault(t2.getId().toString(), 0) <= t2.getHeadcount(),
                "Task B must not exceed headcount 1");
    }

    // -------------------------------------------------------------------------
    // 7. Missing Mandatory Skill Exclusion (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate: volunteer who lacks mandatory skill is never assigned")
    void generateVolunteerLackingMandatorySkillNeverAssigned() throws Exception {
        Event event = createEvent("Mandatory Skill Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        Task task = createTask(event, "Security Lead", date, LocalTime.of(9, 0), LocalTime.of(12, 0), 1,
                List.of("Security"), List.of(), false);

        // Vol 1 lacks Security
        Volunteer vNoSkill = createVolunteer(event, "No Skill", "noskill@test.com",
                List.of(new SkillLevel("Audio", "Expert")),
                List.of(new AvailabilitySlot("Thursday", "Morning")),
                1, 8, List.of());

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.path("assignments").isEmpty(), "Unqualified volunteer must not be assigned to task requiring Security");
    }

    // -------------------------------------------------------------------------
    // 8. Regenerate Replaces Draft (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate twice on a draft: second replaces the first, assignment count correct, no duplicate-key error, single plans row")
    void generateTwiceOnDraftReplacesOldAssignmentsCleanly() throws Exception {
        Event event = createEvent("Regenerate Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        Task task = createTask(event, "Task One", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false);
        createVolunteer(event, "Vol 1", "v1@test.com", List.of(), List.of(new AvailabilitySlot("Thursday", "Morning")), 0, 4, List.of());

        // First generate
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk());

        // Second generate on existing draft
        MvcResult result2 = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result2.getResponse().getContentAsString());
        assertEquals(1, json.path("assignments").size());

        Integer planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM plans WHERE event_id = ?", Integer.class, event.getId());
        assertEquals(1, planCount, "Exactly 1 plan row must exist for event");

        Integer asgCount = jdbcTemplate.queryForObject("SELECT count(*) FROM assignments", Integer.class);
        assertEquals(1, asgCount, "Exactly 1 assignment row must exist in DB (no duplicates from first run)");
    }

    // -------------------------------------------------------------------------
    // 9. Reset Stale Flag (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate resets stale=true to false")
    void generateResetsStaleToFalse() throws Exception {
        Event event = createEvent("Stale Reset Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        createTask(event, "Task Stale", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false);
        createVolunteer(event, "Vol Stale", "stale@test.com", List.of(), List.of(new AvailabilitySlot("Thursday", "Morning")), 0, 4, List.of());

        // Create an existing plan with stale=true
        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(true);
        planRepository.saveAndFlush(plan);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(json.path("stale").asBoolean(), "Plan must no longer be stale after generation");

        Boolean dbStale = jdbcTemplate.queryForObject("SELECT stale FROM plans WHERE id = ?", Boolean.class, plan.getId());
        assertFalse(dbStale, "DB row must reflect stale=false");
    }

    // -------------------------------------------------------------------------
    // 10. Generate on Approved or Published -> 409 and Rows Unchanged (2 methods)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate on approved -> 409 and assignment rows unchanged")
    void generateOnApprovedPlanReturns409AndPreservesRows() throws Exception {
        Event event = createEvent("Approved Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        Task task = createTask(event, "Task Approved", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false);
        Volunteer vol = createVolunteer(event, "Vol Approved", "app@test.com", List.of(), List.of(new AvailabilitySlot("Thursday", "Morning")), 0, 4, List.of());

        Plan plan = new Plan(event.getId(), PlanStatus.APPROVED);
        plan.setApprovedAt(Instant.now());
        plan = planRepository.saveAndFlush(plan);

        // Pre-create an assignment
        Assignment asg = new Assignment();
        asg.setPlan(plan);
        asg.setTask(task);
        asg.setVolunteer(vol);
        asg.setSlotNumber(1);
        asg.setFit(BigDecimal.valueOf(80.0));
        asg.setSkillScore(BigDecimal.valueOf(80.0));
        asg.setExperienceScore(BigDecimal.valueOf(80.0));
        asg.setPerformanceScore(BigDecimal.valueOf(80.0));
        asg.setLoadScore(BigDecimal.valueOf(80.0));
        asg.setInterestScore(BigDecimal.valueOf(80.0));
        asg.setReason("Original reason");
        asg.setFlags(List.of());
        asg.setLocked(false);
        assignmentRepository.saveAndFlush(asg);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan can only be regenerated while it is a draft", json.path("error").asText());

        Integer asgCount = jdbcTemplate.queryForObject("SELECT count(*) FROM assignments WHERE plan_id = ?", Integer.class, plan.getId());
        assertEquals(1, asgCount, "Assignment row must remain untouched");
    }

    @Test
    @DisplayName("generate on published -> 409 and rows unchanged")
    void generateOnPublishedPlanReturns409AndPreservesRows() throws Exception {
        Event event = createEvent("Published Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        Task task = createTask(event, "Task Published", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false);
        Volunteer vol = createVolunteer(event, "Vol Published", "pub@test.com", List.of(), List.of(new AvailabilitySlot("Thursday", "Morning")), 0, 4, List.of());

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setPublishedAt(Instant.now());
        plan = planRepository.saveAndFlush(plan);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan can only be regenerated while it is a draft", json.path("error").asText());

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.PUBLISHED, dbStatus, "DB status must remain published");
    }

    // -------------------------------------------------------------------------
    // 11. Determinism (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("determinism: generate, regenerate produces identical (task, volunteer, slot) triples")
    void generateIsDeterministicAcrossRegenerations() throws Exception {
        Event event = createEvent("Determinism Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        Task t1 = createTask(event, "Task 1", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 2, List.of(), List.of(), false);
        Task t2 = createTask(event, "Task 2", date, LocalTime.of(13, 0), LocalTime.of(15, 0), 1, List.of(), List.of(), false);

        Volunteer v1 = createVolunteer(event, "Vol Alpha", "alpha@test.com", List.of(),
                List.of(new AvailabilitySlot("Thursday", "Morning")), 1, 4, List.of());
        Volunteer v2 = createVolunteer(event, "Vol Beta", "beta@test.com", List.of(),
                List.of(new AvailabilitySlot("Thursday", "Morning")), 2, 4, List.of());
        Volunteer v3 = createVolunteer(event, "Vol Gamma", "gamma@test.com", List.of(),
                List.of(new AvailabilitySlot("Thursday", "Afternoon")), 0, 4, List.of());

        // Run 1
        MvcResult res1 = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json1 = objectMapper.readTree(res1.getResponse().getContentAsString());
        List<String> triples1 = new ArrayList<>();
        for (JsonNode a : json1.path("assignments")) {
            triples1.add(a.path("taskId").asText() + ":" + a.path("volunteerId").asText() + ":" + a.path("slotNumber").asText());
        }

        // Run 2
        MvcResult res2 = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json2 = objectMapper.readTree(res2.getResponse().getContentAsString());
        List<String> triples2 = new ArrayList<>();
        for (JsonNode a : json2.path("assignments")) {
            triples2.add(a.path("taskId").asText() + ":" + a.path("volunteerId").asText() + ":" + a.path("slotNumber").asText());
        }

        assertEquals(triples1, triples2, "Regeneration must produce identical (task, volunteer, slot) triples");
    }

    // -------------------------------------------------------------------------
    // 12. Concurrency (1 method)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("concurrency: two threads generate at once on fresh event: exactly one plan row, no duplicate assignments, no 500s")
    void concurrentGenerateSucceedsWithoutDuplicatePlansOrErrors() throws Exception {
        Event event = createEvent("Concurrent Generate Event");
        LocalDate date = LocalDate.of(2026, 11, 26);
        createTask(event, "Con Task", date, LocalTime.of(9, 0), LocalTime.of(11, 0), 1, List.of(), List.of(), false);
        createVolunteer(event, "Con Vol", "con@test.com", List.of(),
                List.of(new AvailabilitySlot("Thursday", "Morning")), 0, 4, List.of());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger status200 = new AtomicInteger(0);
        AtomicInteger otherErrors = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                                    .header(CSRF_HEADER, CSRF_VALUE)
                                    .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                            .andReturn();

                    int code = res.getResponse().getStatus();
                    if (code == 200) {
                        status200.incrementAndGet();
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent generate requests timed out");
        executor.shutdown();

        assertEquals(2, status200.get(), "Both requests should successfully serialize and return 200");
        assertEquals(0, otherErrors.get(), "No 500s or unexpected errors under concurrent generate");

        Integer planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM plans WHERE event_id = ?", Integer.class, event.getId());
        assertEquals(1, planCount, "Exactly 1 plan row must exist");

        Integer asgCount = jdbcTemplate.queryForObject("SELECT count(*) FROM assignments", Integer.class);
        assertEquals(1, asgCount, "Exactly 1 assignment row must exist in assignments table");
    }

    @Test
    @DisplayName("19) Generate returns 409 when plan size exceeds limit (total slots > MAX_SIZE) and creates no plan row")
    void generatePlanExceedingMaxSizeReturns409AndCreatesNoPlan() throws Exception {
        Event event = createEvent("Oversized Event");
        // Cheapest way to exceed solver's MAX_SIZE (500): 1 task with headcount = 501, and 1 volunteer
        createTask(event, "Big Task", LocalDate.now().plusDays(10), LocalTime.of(9, 0), LocalTime.of(17, 0), 501, List.of(), List.of(), false);
        createVolunteer(event, "Single Volunteer", "single@test.com", List.of(), List.of(), 0, 8, List.of());

        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/generate")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan too large to generate"));

        Integer planCount = jdbcTemplate.queryForObject("SELECT count(*) FROM plans WHERE event_id = ?", Integer.class, event.getId());
        assertEquals(0, planCount, "No plan row should be created when size exceeds limit");
    }
}
