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
public class Step6PlanMineTests {

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private User volunteerUser1;
    private Cookie volunteerCookie1;
    private User volunteerUser2;
    private Cookie volunteerCookie2;
    private User organiserUser;
    private Cookie organiserCookie;

    @BeforeEach
    void setUp() {
        volunteerUser1 = new User();
        volunteerUser1.setEmail("step6_vol1_" + UUID.randomUUID() + "@example.com");
        volunteerUser1.setPasswordHash(passwordEncoder.encode("password"));
        volunteerUser1.setFullName("Step6 Volunteer1");
        volunteerUser1.setRole("volunteer");
        volunteerUser1 = userRepository.save(volunteerUser1);
        volunteerCookie1 = new Cookie("eventra_token", jwtService.generateToken(volunteerUser1.getId(), volunteerUser1.getRole(), volunteerUser1.getTokenVersion()));

        volunteerUser2 = new User();
        volunteerUser2.setEmail("step6_vol2_" + UUID.randomUUID() + "@example.com");
        volunteerUser2.setPasswordHash(passwordEncoder.encode("password"));
        volunteerUser2.setFullName("Step6 Volunteer2");
        volunteerUser2.setRole("volunteer");
        volunteerUser2 = userRepository.save(volunteerUser2);
        volunteerCookie2 = new Cookie("eventra_token", jwtService.generateToken(volunteerUser2.getId(), volunteerUser2.getRole(), volunteerUser2.getTokenVersion()));

        organiserUser = new User();
        organiserUser.setEmail("step6_org_" + UUID.randomUUID() + "@example.com");
        organiserUser.setPasswordHash(passwordEncoder.encode("password"));
        organiserUser.setFullName("Step6 Organiser");
        organiserUser.setRole("organiser");
        organiserUser = userRepository.save(organiserUser);
        organiserCookie = new Cookie("eventra_token", jwtService.generateToken(organiserUser.getId(), organiserUser.getRole(), organiserUser.getTokenVersion()));
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            planRepository.findByEventId(eventId).ifPresent(p -> {
                jdbcTemplate.update("DELETE FROM assignments WHERE plan_id = ?", p.getId());
                jdbcTemplate.update("DELETE FROM plans WHERE id = ?", p.getId());
            });
            jdbcTemplate.update("DELETE FROM tasks WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    private Event createEvent() {
        Event event = new Event();
        event.setName("Step 6 Event " + UUID.randomUUID());
        event.setDescription("Step 6 test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    @Test
    void publishedPlan_returnsOnlyCallersAssignments() throws Exception {
        Event event = createEvent();

        Task task1 = new Task();
        task1.setEventId(event.getId());
        task1.setTitle("Task for Vol1");
        task1.setTaskDate(LocalDate.now().plusDays(2));
        task1.setStartTime(LocalTime.of(9, 0));
        task1.setEndTime(LocalTime.of(12, 0));
        task1.setHeadcount(1);
        task1.setStatus("pending");
        task1 = taskRepository.save(task1);

        Task task2 = new Task();
        task2.setEventId(event.getId());
        task2.setTitle("Task for Vol2");
        task2.setTaskDate(LocalDate.now().plusDays(2));
        task2.setStartTime(LocalTime.of(14, 0));
        task2.setEndTime(LocalTime.of(17, 0));
        task2.setHeadcount(1);
        task2.setStatus("pending");
        task2 = taskRepository.save(task2);

        Volunteer vol1 = new Volunteer();
        vol1.setEventId(event.getId());
        vol1.setUserId(volunteerUser1.getId());
        vol1.setFullName("Step6 Volunteer1");
        vol1.setEmail(volunteerUser1.getEmail());
        vol1.setPhone("9876543210");
        vol1.setStatus("active");
        vol1 = volunteerRepository.save(vol1);

        Volunteer vol2 = new Volunteer();
        vol2.setEventId(event.getId());
        vol2.setUserId(volunteerUser2.getId());
        vol2.setFullName("Step6 Volunteer2");
        vol2.setEmail(volunteerUser2.getEmail());
        vol2.setPhone("9876543211");
        vol2.setStatus("active");
        vol2 = volunteerRepository.save(vol2);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Assignment a1 = new Assignment();
        a1.setPlan(plan);
        a1.setTask(task1);
        a1.setVolunteer(vol1);
        a1.setSlotNumber(1);
        a1.setFit(BigDecimal.valueOf(95.0));
        a1.setSkillScore(BigDecimal.valueOf(1.0));
        a1.setExperienceScore(BigDecimal.valueOf(1.0));
        a1.setPerformanceScore(BigDecimal.valueOf(1.0));
        a1.setLoadScore(BigDecimal.valueOf(1.0));
        a1.setInterestScore(BigDecimal.valueOf(1.0));
        a1.setReason("Vol1 match");
        a1.setFlags(List.of());
        assignmentRepository.save(a1);

        Assignment a2 = new Assignment();
        a2.setPlan(plan);
        a2.setTask(task2);
        a2.setVolunteer(vol2);
        a2.setSlotNumber(1);
        a2.setFit(BigDecimal.valueOf(80.0));
        a2.setSkillScore(BigDecimal.valueOf(0.5));
        a2.setExperienceScore(BigDecimal.valueOf(0.5));
        a2.setPerformanceScore(BigDecimal.valueOf(0.5));
        a2.setLoadScore(BigDecimal.valueOf(1.0));
        a2.setInterestScore(BigDecimal.valueOf(0.0));
        a2.setReason("Vol2 match");
        a2.setFlags(List.of());
        assignmentRepository.save(a2);

        // Vol 1 sees only task 1
        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].taskId").value(task1.getId().toString()))
                .andExpect(jsonPath("$[0].taskTitle").value("Task for Vol1"))
                .andExpect(jsonPath("$[0].startTime").value("09:00"))
                .andExpect(jsonPath("$[0].endTime").value("12:00"));
    }

    @Test
    void planNone_returns404() throws Exception {
        Event event = createEvent();

        Volunteer vol1 = new Volunteer();
        vol1.setEventId(event.getId());
        vol1.setUserId(volunteerUser1.getId());
        vol1.setFullName("Step6 Volunteer1");
        vol1.setEmail(volunteerUser1.getEmail());
        vol1.setPhone("9876543210");
        vol1.setStatus("active");
        volunteerRepository.save(vol1);

        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void planDraft_returns404() throws Exception {
        Event event = createEvent();

        Volunteer vol1 = new Volunteer();
        vol1.setEventId(event.getId());
        vol1.setUserId(volunteerUser1.getId());
        vol1.setFullName("Step6 Volunteer1");
        vol1.setEmail(volunteerUser1.getEmail());
        vol1.setPhone("9876543210");
        vol1.setStatus("active");
        volunteerRepository.save(vol1);

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        planRepository.save(plan);

        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void planApproved_returns404() throws Exception {
        Event event = createEvent();

        Volunteer vol1 = new Volunteer();
        vol1.setEventId(event.getId());
        vol1.setUserId(volunteerUser1.getId());
        vol1.setFullName("Step6 Volunteer1");
        vol1.setEmail(volunteerUser1.getEmail());
        vol1.setPhone("9876543210");
        vol1.setStatus("active");
        volunteerRepository.save(vol1);

        Plan plan = new Plan(event.getId(), PlanStatus.APPROVED);
        planRepository.save(plan);

        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void volunteerNotInEvent_returns404() throws Exception {
        Event event = createEvent();

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        planRepository.save(plan);

        // volunteerUser1 is not registered in this event
        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void organiserGets403RoleCheck() throws Exception {
        Event event = createEvent();

        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRejected() throws Exception {
        Event event = createEvent();

        mockMvc.perform(get("/api/events/" + event.getId() + "/plan/mine")
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }
}

