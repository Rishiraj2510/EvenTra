package com.eventra.feedback;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.plan.Assignment;
import com.eventra.plan.AssignmentRepository;
import com.eventra.plan.Plan;
import com.eventra.plan.PlanRepository;
import com.eventra.plan.PlanStatus;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class FeedbackEndpointGetTests {

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
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private Cookie organiserCookie;
    private Cookie volunteerCookie;

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("fb_get_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Feedback Organiser");
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        organiserCookie = new Cookie("eventra_token", jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion()));

        User volunteer = new User();
        volunteer.setEmail("fb_get_vol_" + UUID.randomUUID() + "@example.com");
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
        event.setName("FB Event " + UUID.randomUUID());
        event.setDescription("Feedback test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private Assignment createAssignment(Plan plan, Task task, Volunteer volunteer) {
        Assignment a = new Assignment();
        a.setPlan(plan);
        a.setTask(task);
        a.setVolunteer(volunteer);
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
    void publishedPlan_returnsRowPerAssignment_withExactValuesAndNullWhenNoFeedback() throws Exception {
        Event event = createEvent();

        Task task1 = new Task();
        task1.setEventId(event.getId());
        task1.setTitle("Task 1");
        task1.setTaskDate(LocalDate.now().plusDays(2));
        task1.setStartTime(LocalTime.of(9, 0));
        task1.setEndTime(LocalTime.of(12, 0));
        task1.setHeadcount(1);
        task1.setStatus("pending");
        task1 = taskRepository.save(task1);

        Task task2 = new Task();
        task2.setEventId(event.getId());
        task2.setTitle("Task 2");
        task2.setTaskDate(LocalDate.now().plusDays(2));
        task2.setStartTime(LocalTime.of(13, 0));
        task2.setEndTime(LocalTime.of(16, 0));
        task2.setHeadcount(1);
        task2.setStatus("pending");
        task2 = taskRepository.save(task2);

        Volunteer v1 = new Volunteer();
        v1.setEventId(event.getId());
        v1.setFullName("Alice Smith");
        v1.setEmail("alice" + UUID.randomUUID() + "@example.com");
        v1.setPhone("9876543210");
        v1.setStatus("active");
        v1 = volunteerRepository.save(v1);

        Volunteer v2 = new Volunteer();
        v2.setEventId(event.getId());
        v2.setFullName("Bob Jones");
        v2.setEmail("bob" + UUID.randomUUID() + "@example.com");
        v2.setPhone("9876543211");
        v2.setStatus("active");
        v2 = volunteerRepository.save(v2);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Assignment a1 = createAssignment(plan, task1, v1);
        Assignment a2 = createAssignment(plan, task2, v2);

        // Give feedback only to a1
        Feedback fb1 = new Feedback(a1, (short) 5, "Great job!");
        feedbackRepository.save(fb1);

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assignmentId").value(a1.getId().toString()))
                .andExpect(jsonPath("$[0].volunteerId").value(v1.getId().toString()))
                .andExpect(jsonPath("$[0].volunteerName").value("Alice Smith"))
                .andExpect(jsonPath("$[0].taskTitle").value("Task 1"))
                .andExpect(jsonPath("$[0].rating").value(5))
                .andExpect(jsonPath("$[0].comment").value("Great job!"))
                .andExpect(jsonPath("$[1].assignmentId").value(a2.getId().toString()))
                .andExpect(jsonPath("$[1].volunteerId").value(v2.getId().toString()))
                .andExpect(jsonPath("$[1].volunteerName").value("Bob Jones"))
                .andExpect(jsonPath("$[1].taskTitle").value("Task 2"))
                .andExpect(jsonPath("$[1].rating").doesNotExist())
                .andExpect(jsonPath("$[1].comment").doesNotExist());
    }

    @Test
    void draftPlan_returns409() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        planRepository.save(plan);

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));
    }

    @Test
    void approvedPlan_returns409() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.APPROVED);
        planRepository.save(plan);

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));
    }

    @Test
    void noPlan_returns409() throws Exception {
        Event event = createEvent();

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));
    }

    @Test
    void unknownEvent_returns404() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/events/" + randomId + "/feedback")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found: " + randomId));
    }

    @Test
    void feedbackSortedByDateStartTitleVolunteerAssignment() throws Exception {
        Event event = createEvent();

        LocalDate d1 = LocalDate.now().plusDays(2);
        LocalDate d2 = LocalDate.now().plusDays(3);

        Task t1 = new Task();
        t1.setEventId(event.getId());
        t1.setTitle("B Task");
        t1.setTaskDate(d1);
        t1.setStartTime(LocalTime.of(9, 0));
        t1.setEndTime(LocalTime.of(12, 0));
        t1 = taskRepository.save(t1);

        Task t2 = new Task();
        t2.setEventId(event.getId());
        t2.setTitle("A Task");
        t2.setTaskDate(d1);
        t2.setStartTime(LocalTime.of(9, 0));
        t2.setEndTime(LocalTime.of(12, 0));
        t2 = taskRepository.save(t2);

        Task t3 = new Task();
        t3.setEventId(event.getId());
        t3.setTitle("Later Task");
        t3.setTaskDate(d2);
        t3.setStartTime(LocalTime.of(8, 0));
        t3.setEndTime(LocalTime.of(11, 0));
        t3 = taskRepository.save(t3);

        Volunteer v1 = new Volunteer();
        v1.setEventId(event.getId());
        v1.setFullName("Zoe");
        v1.setEmail("zoe" + UUID.randomUUID() + "@example.com");
        v1.setPhone("9876543210");
        v1.setStatus("active");
        v1 = volunteerRepository.save(v1);

        Volunteer v2 = new Volunteer();
        v2.setEventId(event.getId());
        v2.setFullName("Amy");
        v2.setEmail("amy" + UUID.randomUUID() + "@example.com");
        v2.setPhone("9876543211");
        v2.setStatus("active");
        v2 = volunteerRepository.save(v2);

        Volunteer v3 = new Volunteer();
        v3.setEventId(event.getId());
        v3.setFullName("Ben");
        v3.setEmail("ben" + UUID.randomUUID() + "@example.com");
        v3.setPhone("9876543212");
        v3.setStatus("active");
        v3 = volunteerRepository.save(v3);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        // a1: B Task (d1, 9:00) with Zoe
        Assignment a1 = createAssignment(plan, t1, v1);
        // a2: A Task (d1, 9:00) with Amy -> should come first (A Task before B Task)
        Assignment a2 = createAssignment(plan, t2, v2);
        // a3: Later Task (d2, 8:00) with Ben -> should come last (d2 > d1)
        Assignment a3 = createAssignment(plan, t3, v3);

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].assignmentId").value(a2.getId().toString()))
                .andExpect(jsonPath("$[1].assignmentId").value(a1.getId().toString()))
                .andExpect(jsonPath("$[2].assignmentId").value(a3.getId().toString()));
    }

    @Test
    void volunteerRoleGets403() throws Exception {
        Event event = createEvent();

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .cookie(volunteerCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        Event event = createEvent();

        mockMvc.perform(get("/api/events/" + event.getId() + "/feedback")
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }
}

