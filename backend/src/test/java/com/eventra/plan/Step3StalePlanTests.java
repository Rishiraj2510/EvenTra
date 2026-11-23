package com.eventra.plan;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.task.dto.UpdateTaskRequest;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class Step3StalePlanTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private com.eventra.task.TaskService taskService;

    @Autowired
    private PlanRepository planRepository;

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
        organiser.setEmail("step3_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Step3 Organiser");
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        String token = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());
        organiserCookie = new Cookie("eventra_token", token);
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
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
        event.setName("Step 3 Event " + UUID.randomUUID());
        event.setDescription("Step 3 test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private Task createBaseTask(UUID eventId) {
        Task task = new Task();
        task.setEventId(eventId);
        task.setTitle("Base Task");
        task.setDescription("Base Description");
        task.setTaskDate(LocalDate.now().plusDays(2));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(2);
        task.setMandatorySkills(List.of("Java"));
        task.setPreferredSkills(List.of("Spring"));
        task.setLocation("Hall A");
        task.setStatus("pending");
        return taskRepository.save(task);
    }

    private UpdateTaskRequest createValidUpdateRequest(Task task) {
        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle(task.getTitle());
        req.setDescription(task.getDescription());
        req.setDate(task.getTaskDate());
        req.setStartTime(task.getStartTime());
        req.setEndTime(task.getEndTime());
        req.setHeadcount(task.getHeadcount());
        req.setMandatorySkills(task.getMandatorySkills());
        req.setPreferredSkills(task.getPreferredSkills());
        req.setCritical(task.isCritical());
        req.setLocation(task.getLocation());
        req.setStatus("pending");
        return req;
    }

    @Test
    void updateTask_timeChange_setsStaleTrue() throws Exception {
        Event event = createEvent();
        Task task = createBaseTask(event.getId());

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(false);
        plan = planRepository.save(plan);

        UpdateTaskRequest req = createValidUpdateRequest(task);
        req.setStartTime(LocalTime.of(10, 0)); // changed
        req.setEndTime(LocalTime.of(13, 0));

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertTrue(updated.isStale(), "Plan must be marked stale on time change");
    }

    @Test
    void updateTask_headcountChange_setsStaleTrue() throws Exception {
        Event event = createEvent();
        Task task = createBaseTask(event.getId());

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(false);
        plan = planRepository.save(plan);

        UpdateTaskRequest req = createValidUpdateRequest(task);
        req.setHeadcount(3); // changed from 2 to 3

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertTrue(updated.isStale(), "Plan must be marked stale on headcount change");
    }

    @Test
    void updateTask_skillsChange_setsStaleTrue() throws Exception {
        Event event = createEvent();
        Task task = createBaseTask(event.getId());

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(false);
        plan = planRepository.save(plan);

        UpdateTaskRequest req = createValidUpdateRequest(task);
        req.setMandatorySkills(List.of("Java", "Security")); // changed

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertTrue(updated.isStale(), "Plan must be marked stale on skills change");
    }

    @Test
    void updateTask_titleOnlyChange_staleNotSet() throws Exception {
        Event event = createEvent();
        Task task = createBaseTask(event.getId());

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(false);
        plan = planRepository.save(plan);

        UpdateTaskRequest req = createValidUpdateRequest(task);
        req.setTitle("Updated Title Only"); // changed title only

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertFalse(updated.isStale(), "Plan stale must stay false on title-only change");
    }

    @Test
    void updateTask_headcount200TitleOnlyChange_staleNotSet() {
        Event event = createEvent();
        Task task = createBaseTask(event.getId());
        task.setHeadcount(200);
        task = taskRepository.save(task);

        Plan plan = new Plan(event.getId(), PlanStatus.DRAFT);
        plan.setStale(false);
        plan = planRepository.save(plan);

        UpdateTaskRequest req = createValidUpdateRequest(task);
        req.setHeadcount(200);
        req.setTitle("New Title For 200 Headcount");

        taskService.update(task.getId(), req);

        Plan updated = planRepository.findByEventId(event.getId()).orElseThrow();
        assertFalse(updated.isStale(), "Plan stale must stay false on title-only change with headcount 200");
    }

    @Test
    void updateTask_noPlanRow_noError() throws Exception {
        Event event = createEvent();
        Task task = createBaseTask(event.getId());

        UpdateTaskRequest req = createValidUpdateRequest(task);
        req.setTitle("Updated Without Plan");

        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertTrue(planRepository.findByEventId(event.getId()).isEmpty(), "No plan row should exist");
    }
}

