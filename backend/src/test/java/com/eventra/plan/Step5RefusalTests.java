package com.eventra.plan;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.registration.RegistrationForm;
import com.eventra.registration.RegistrationFormRepository;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class Step5RefusalTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationFormRepository registrationFormRepository;

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
    private User volunteerUser;
    private Cookie volunteerCookie;

    @BeforeEach
    void setUp() {
        volunteerUser = new User();
        volunteerUser.setEmail("step5_vol_" + UUID.randomUUID() + "@example.com");
        volunteerUser.setPasswordHash(passwordEncoder.encode("password"));
        volunteerUser.setFullName("Step5 Volunteer");
        volunteerUser.setRole("volunteer");
        volunteerUser = userRepository.save(volunteerUser);
        String token = jwtService.generateToken(volunteerUser.getId(), volunteerUser.getRole(), volunteerUser.getTokenVersion());
        volunteerCookie = new Cookie("eventra_token", token);
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            planRepository.findByEventId(eventId).ifPresent(p -> {
                jdbcTemplate.update("DELETE FROM assignments WHERE plan_id = ?", p.getId());
                jdbcTemplate.update("DELETE FROM plans WHERE id = ?", p.getId());
            });
            jdbcTemplate.update("DELETE FROM registration_forms WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM tasks WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    private Event createEvent() {
        Event event = new Event();
        event.setName("Step 5 Event " + UUID.randomUUID());
        event.setDescription("Step 5 test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    @Test
    void cancelRegistrationWithAssignments_returns409AndRowUnchanged() throws Exception {
        Event event = createEvent();

        RegistrationForm form = new RegistrationForm();
        form.setEventId(event.getId());
        form.setStatus("open");
        registrationFormRepository.save(form);

        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle("Registration Desk");
        task.setTaskDate(LocalDate.now().plusDays(2));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(1);
        task.setMandatorySkills(List.of("Communication"));
        task.setStatus("pending");
        task = taskRepository.save(task);

        Volunteer volunteer = new Volunteer();
        volunteer.setEventId(event.getId());
        volunteer.setUserId(volunteerUser.getId());
        volunteer.setFullName("Step5 Volunteer");
        volunteer.setEmail(volunteerUser.getEmail());
        volunteer.setPhone("9876543210");
        volunteer.setStatus("active");
        volunteer = volunteerRepository.save(volunteer);

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Assignment assignment = new Assignment();
        assignment.setPlan(plan);
        assignment.setTask(task);
        assignment.setVolunteer(volunteer);
        assignment.setSlotNumber(1);
        assignment.setFit(BigDecimal.valueOf(90.0));
        assignment.setSkillScore(BigDecimal.valueOf(1.0));
        assignment.setExperienceScore(BigDecimal.valueOf(0.5));
        assignment.setPerformanceScore(BigDecimal.valueOf(0.5));
        assignment.setLoadScore(BigDecimal.valueOf(1.0));
        assignment.setInterestScore(BigDecimal.valueOf(0.0));
        assignment.setReason("Desk assignment");
        assignment.setFlags(List.of());
        assignmentRepository.save(assignment);

        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(volunteerCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Volunteer has assignments; cancellation not allowed"));

        assertTrue(volunteerRepository.existsById(volunteer.getId()), "Volunteer row must remain unchanged");
    }
}
