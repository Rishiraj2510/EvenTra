package com.eventra.feedback;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.feedback.dto.UpsertFeedbackRequest;
import com.eventra.plan.Assignment;
import com.eventra.plan.AssignmentRepository;
import com.eventra.plan.Plan;
import com.eventra.plan.PlanRepository;
import com.eventra.plan.PlanStatus;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class FeedbackEndpointPutTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

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
    private FeedbackRepository feedbackRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private Cookie organiserCookie;
    private Cookie volunteerCookie;

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("fb_put_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Feedback Organiser");
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        organiserCookie = new Cookie("eventra_token", jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion()));

        User volunteer = new User();
        volunteer.setEmail("fb_put_vol_" + UUID.randomUUID() + "@example.com");
        volunteer.setPasswordHash(passwordEncoder.encode("password"));
        volunteer.setFullName("Feedback Volunteer");
        volunteer.setRole("volunteer");
        volunteer = userRepository.save(volunteer);
        volunteerCookie = new Cookie("eventra_token", jwtService.generateToken(volunteer.getId(), volunteer.getRole(), volunteer.getTokenVersion()));
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            jdbcTemplate.update("DELETE FROM feedback WHERE assignment_id IN (SELECT id FROM assignments WHERE plan_id IN (SELECT id FROM plans WHERE event_id = ?))", eventId);
            jdbcTemplate.update("DELETE FROM assignments WHERE plan_id IN (SELECT id FROM plans WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM plans WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM tasks WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    private Event createEvent() {
        Event event = new Event();
        event.setName("FB Put Event " + UUID.randomUUID());
        event.setDescription("Feedback test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private Assignment createAssignment(Plan plan) {
        Task task = new Task();
        task.setEventId(plan.getEventId());
        task.setTitle("Setup Room");
        task.setTaskDate(LocalDate.now().plusDays(2));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(1);
        task.setStatus("pending");
        task = taskRepository.save(task);

        Volunteer v = new Volunteer();
        v.setEventId(plan.getEventId());
        v.setFullName("Charlie Brown");
        v.setEmail("charlie" + UUID.randomUUID() + "@example.com");
        v.setPhone("9876543210");
        v.setStatus("active");
        v = volunteerRepository.save(v);

        Assignment a = new Assignment();
        a.setPlan(plan);
        a.setTask(task);
        a.setVolunteer(v);
        a.setSlotNumber(1);
        a.setFit(BigDecimal.valueOf(90.0));
        a.setSkillScore(BigDecimal.valueOf(1.0));
        a.setExperienceScore(BigDecimal.valueOf(1.0));
        a.setPerformanceScore(BigDecimal.valueOf(1.0));
        a.setLoadScore(BigDecimal.valueOf(1.0));
        a.setInterestScore(BigDecimal.valueOf(1.0));
        a.setReason("Good fit");
        a.setFlags(List.of());
        return assignmentRepository.save(a);
    }

    @Test
    void createsFeedbackAndSecondPutReplacesItLeavingOneRow() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req1 = new UpsertFeedbackRequest((short) 4, "Initial feedback");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.comment").value("Initial feedback"));

        Feedback fb1 = feedbackRepository.findByAssignmentId(assignment.getId()).orElseThrow();
        assertEquals(4, fb1.getRating());
        assertEquals("Initial feedback", fb1.getComment());

        // Second PUT replaces it
        UpsertFeedbackRequest req2 = new UpsertFeedbackRequest((short) 5, "Updated feedback");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Updated feedback"));

        assertEquals(1, feedbackRepository.findAll().stream().filter(f -> f.getAssignment().getId().equals(assignment.getId())).count());
        Feedback fb2 = feedbackRepository.findByAssignmentId(assignment.getId()).orElseThrow();
        assertEquals(5, fb2.getRating());
        assertEquals("Updated feedback", fb2.getComment());
    }

    @Test
    void rating0_returns400() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 0, "Valid comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rating6_returns400() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 6, "Valid comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ratingNull_returns400() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest(null, "Valid comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commentOf300CharsAccepted() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        String comment300 = "a".repeat(300);
        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, comment300);

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value(comment300));
    }

    @Test
    void commentOf301Chars_returns400() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        String comment301 = "a".repeat(301);
        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, comment301);

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void controlCharInComment_returns400() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "Hello\u0007World");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("comment contains invalid characters"));
    }

    @Test
    void commentOkFollowedByNulChar_returns400AndCreatesNoRow() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "ok\u0000");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("comment contains invalid characters"));

        assertTrue(feedbackRepository.findByAssignmentId(assignment.getId()).isEmpty(), "No feedback row should be created");
    }

    @Test
    void rating70000InJsonBody_returns400Not500() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        String jsonBody = "{\"rating\": 70000, \"comment\": \"Great\"}";

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankCommentStoredAsNull() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "   ");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").doesNotExist());

        Feedback fb = feedbackRepository.findByAssignmentId(assignment.getId()).orElseThrow();
        assertNull(fb.getComment());
    }

    @Test
    void planNotPublished_returns409AndNoRowCreated() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "Comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));

        assertTrue(feedbackRepository.findByAssignmentId(assignment.getId()).isEmpty());
    }

    @Test
    void assignmentOfDifferentEvent_returns404AndNoRowCreated() throws Exception {
        Event event1 = createEvent();
        Plan plan1 = new Plan(event1.getId(), PlanStatus.PUBLISHED);
        plan1 = planRepository.save(plan1);
        Assignment assignment1 = createAssignment(plan1);

        Event event2 = createEvent();
        Plan plan2 = new Plan(event2.getId(), PlanStatus.PUBLISHED);
        plan2 = planRepository.save(plan2);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "Comment");

        // Request PUT on event2 with assignment1 (belonging to event1)
        mockMvc.perform(put("/api/events/" + event2.getId() + "/feedback/" + assignment1.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Assignment not found"));

        assertTrue(feedbackRepository.findByAssignmentId(assignment1.getId()).isEmpty());
    }

    @Test
    void unknownAssignment_returns404() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);

        UUID randomAssignmentId = UUID.randomUUID();
        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "Comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + randomAssignmentId)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Assignment not found"));
    }

    @Test
    void volunteerRoleGets403() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "Comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .cookie(volunteerCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);
        Assignment assignment = createAssignment(plan);

        UpsertFeedbackRequest req = new UpsertFeedbackRequest((short) 4, "Comment");

        mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}

