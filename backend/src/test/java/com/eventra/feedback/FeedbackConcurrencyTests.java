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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class FeedbackConcurrencyTests {

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

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("fb_conc_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Feedback Organiser");
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        organiserCookie = new Cookie("eventra_token", jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion()));
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
        event.setName("FB Conc Event " + UUID.randomUUID());
        event.setDescription("Feedback test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    @Test
    void twoSimultaneousFirstPutsForSameAssignment_endWithExactlyOneRowAndNo500() throws Exception {
        Event event = createEvent();
        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle("Task Conc");
        task.setTaskDate(LocalDate.now().plusDays(2));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(1);
        task.setStatus("pending");
        task = taskRepository.save(task);

        Volunteer v = new Volunteer();
        v.setEventId(event.getId());
        v.setFullName("Volunteer Conc");
        v.setEmail("volconc" + UUID.randomUUID() + "@example.com");
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
        Assignment assignment = assignmentRepository.save(a);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger status500Count = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final short rating = (short) (4 + i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    UpsertFeedbackRequest req = new UpsertFeedbackRequest(rating, "Comment from thread " + rating);
                    int status = mockMvc.perform(put("/api/events/" + event.getId() + "/feedback/" + assignment.getId())
                                    .cookie(organiserCookie)
                                    .header(CSRF_HEADER, CSRF_VALUE)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(req)))
                            .andReturn().getResponse().getStatus();

                    if (status >= 200 && status < 300) {
                        successCount.incrementAndGet();
                    } else if (status == 500) {
                        status500Count.incrementAndGet();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(0, status500Count.get(), "No request should return 500");
        assertEquals(2, successCount.get(), "Both requests should successfully complete");
        assertEquals(1, feedbackRepository.findAll().stream().filter(f -> f.getAssignment().getId().equals(assignment.getId())).count(),
                "Exactly one feedback row must exist");
    }
}

