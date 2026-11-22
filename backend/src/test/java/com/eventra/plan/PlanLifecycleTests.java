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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
class PlanLifecycleTests {

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
        organiser.setEmail("organiser_lifecycle_test@acropolis.in");
        organiser.setFullName("Organiser Lifecycle Tester");
        organiser.setPasswordHash(passwordEncoder.encode("OrganiserPass123!"));
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());

        volunteerUser = new User();
        volunteerUser.setEmail("volunteer_lifecycle_test@acropolis.in");
        volunteerUser.setFullName("Volunteer Lifecycle Tester");
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
        event.setDescription("Lifecycle test event description");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        return eventRepository.save(event);
    }

    // -------------------------------------------------------------------------
    // Approve Tests (8 methods)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("approve: unauthenticated returns 401")
    void approveUnauthenticatedReturns401() throws Exception {
        UUID eventId = UUID.randomUUID();
        mockMvc.perform(post("/api/events/" + eventId + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("approve: volunteer returns 403")
    void approveVolunteerReturns403() throws Exception {
        Event event = createEvent("Approve 403 Event");
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, volunteerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approve: unknown event returns 404")
    void approveUnknownEventReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/events/" + unknownId + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Event not found", json.path("error").asText());
    }

    @Test
    @DisplayName("approve: missing X-Requested-With header rejected")
    void approveMissingCsrfHeaderRejected() throws Exception {
        Event event = createEvent("Approve CSRF Event");
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approve: no plan returns 409")
    void approveNoPlanReturns409() throws Exception {
        Event event = createEvent("Approve No Plan Event");
        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan must be a draft to approve", json.path("error").asText());
    }

    @Test
    @DisplayName("approve: draft plan succeeds with 200, status approved, approvedAt set")
    void approveDraftPlanSucceeds() throws Exception {
        Event event = createEvent("Approve Draft Event");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.APPROVED, json.path("status").asText());

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals(PlanStatus.APPROVED, updated.getStatus());
        assertNotNull(updated.getApprovedAt(), "approvedAt must be set");

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.APPROVED, dbStatus);
    }

    @Test
    @DisplayName("approve: already approved plan returns 409 and status unchanged")
    void approveApprovedPlanReturns409() throws Exception {
        Event event = createEvent("Approve Approved Event");
        Plan plan = new Plan(event.getId(), PlanStatus.APPROVED);
        plan.setApprovedAt(Instant.now());
        plan = planRepository.saveAndFlush(plan);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan must be a draft to approve", json.path("error").asText());

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.APPROVED, dbStatus, "Plan status in DB must remain unchanged after rejected approve");
    }

    @Test
    @DisplayName("approve: published plan returns 409 and status unchanged")
    void approvePublishedPlanReturns409() throws Exception {
        Event event = createEvent("Approve Published Event");
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setPublishedAt(Instant.now());
        plan = planRepository.saveAndFlush(plan);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan must be a draft to approve", json.path("error").asText());

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.PUBLISHED, dbStatus, "Plan status in DB must remain unchanged after rejected approve");
    }

    // -------------------------------------------------------------------------
    // Publish Tests (7 methods)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("publish: unauthenticated returns 401")
    void publishUnauthenticatedReturns401() throws Exception {
        UUID eventId = UUID.randomUUID();
        mockMvc.perform(post("/api/events/" + eventId + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("publish: volunteer returns 403")
    void publishVolunteerReturns403() throws Exception {
        Event event = createEvent("Publish 403 Event");
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, volunteerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("publish: unknown event returns 404")
    void publishUnknownEventReturns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/events/" + unknownId + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Event not found", json.path("error").asText());
    }

    @Test
    @DisplayName("publish: no plan returns 409")
    void publishNoPlanReturns409() throws Exception {
        Event event = createEvent("Publish No Plan Event");
        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan must be approved to publish", json.path("error").asText());
    }

    @Test
    @DisplayName("publish: draft plan returns 409 and status unchanged")
    void publishDraftPlanReturns409() throws Exception {
        Event event = createEvent("Publish Draft Event");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan must be approved to publish", json.path("error").asText());

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.DRAFT, dbStatus, "Plan status in DB must remain draft after rejected publish");
    }

    @Test
    @DisplayName("publish: approved plan succeeds with 200, status published, publishedAt set")
    void publishApprovedPlanSucceeds() throws Exception {
        Event event = createEvent("Publish Approved Event");
        Plan plan = new Plan(event.getId(), PlanStatus.APPROVED);
        plan.setApprovedAt(Instant.now());
        plan = planRepository.saveAndFlush(plan);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(PlanStatus.PUBLISHED, json.path("status").asText());

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals(PlanStatus.PUBLISHED, updated.getStatus());
        assertNotNull(updated.getPublishedAt(), "publishedAt must be set");

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.PUBLISHED, dbStatus);
    }

    @Test
    @DisplayName("publish: published again returns 409 and status unchanged")
    void publishPublishedAgainReturns409() throws Exception {
        Event event = createEvent("Publish Again Event");
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setPublishedAt(Instant.now());
        plan = planRepository.saveAndFlush(plan);

        MvcResult result = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Plan must be approved to publish", json.path("error").asText());

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.PUBLISHED, dbStatus, "Plan status in DB must remain published after second publish call");
    }

    // -------------------------------------------------------------------------
    // Concurrency & Row Invariance Tests (2 methods)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("concurrency: two threads approve the same draft at once: exactly one 200 and one 409, final status approved")
    void concurrentApproveReturnsOne200AndOne409() throws Exception {
        Event event = createEvent("Concurrent Approve Event");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger status200 = new AtomicInteger(0);
        AtomicInteger status409 = new AtomicInteger(0);
        AtomicInteger otherStatus = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                                    .header(CSRF_HEADER, CSRF_VALUE)
                                    .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                            .andReturn();

                    int code = res.getResponse().getStatus();
                    if (code == 200) {
                        status200.incrementAndGet();
                    } else if (code == 409) {
                        status409.incrementAndGet();
                    } else {
                        otherStatus.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherStatus.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "Concurrent approve requests timed out");
        executor.shutdown();

        assertEquals(1, status200.get(), "Exactly one approve request must return 200");
        assertEquals(1, status409.get(), "The competing approve request must return 409");
        assertEquals(0, otherStatus.get(), "No unexpected status codes (500, etc.)");

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.APPROVED, dbStatus, "Final DB status must be approved");
    }

    @Test
    @DisplayName("invariance: after a rejected transition, assert the plan row status is unchanged")
    void rejectedTransitionLeavesPlanRowUnchanged() throws Exception {
        Event event = createEvent("Invariance Event");
        Plan plan = planRepository.saveAndFlush(new Plan(event.getId(), PlanStatus.DRAFT));

        // Call publish on draft plan -> 409
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/publish")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict());

        String dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.DRAFT, dbStatus, "DB status must remain draft after rejected publish");

        // Transition to approved
        plan.setStatus(PlanStatus.APPROVED);
        plan.setApprovedAt(Instant.now());
        planRepository.saveAndFlush(plan);

        // Call approve on approved plan -> 409
        mockMvc.perform(post("/api/events/" + event.getId() + "/plan/approve")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .cookie(new Cookie(COOKIE_NAME, organiserToken)))
                .andExpect(status().isConflict());

        dbStatus = jdbcTemplate.queryForObject("SELECT status FROM plans WHERE id = ?", String.class, plan.getId());
        assertEquals(PlanStatus.APPROVED, dbStatus, "DB status must remain approved after rejected approve");
    }
}
