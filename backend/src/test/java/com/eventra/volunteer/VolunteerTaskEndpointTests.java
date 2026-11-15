package com.eventra.volunteer;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class VolunteerTaskEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User organiser;
    private User volunteerA;
    private User volunteerB;
    private String organiserToken;
    private String volunteerAToken;
    private String volunteerBToken;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        organiser = createUser("organiser");
        volunteerA = createUser("volunteer");
        volunteerB = createUser("volunteer");
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());
        volunteerAToken = jwtService.generateToken(volunteerA.getId(), volunteerA.getRole(), volunteerA.getTokenVersion());
        volunteerBToken = jwtService.generateToken(volunteerB.getId(), volunteerB.getRole(), volunteerB.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            try {
                eventRepository.deleteById(eventId);
            } catch (Exception ignored) {
            }
        }
        createdEventIds.clear();

        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();
    }

    private User createUser(String role) {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setEmail("test_" + randomSuffix + "@eventra.test");
        user.setFullName("Test " + randomSuffix);
        user.setRole(role);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setTokenVersion(0);
        user = userRepository.save(user);
        createdUserIds.add(user.getId());
        return user;
    }

    private Event createEvent(String name, String status) {
        Event event = new Event();
        event.setName(name);
        event.setDescription("Test Description");
        event.setStartDate(Instant.now().plusSeconds(3600));
        event.setEndDate(Instant.now().plusSeconds(7200));
        event.setStatus(status);
        event.setCreatedBy(organiser.getId());
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private Task createTask(UUID eventId, String title, LocalDate date, LocalTime start, LocalTime end) {
        Task task = new Task();
        task.setEventId(eventId);
        task.setTitle(title);
        task.setDescription("Description for " + title);
        task.setTaskDate(date);
        task.setStartTime(start);
        task.setEndTime(end);
        task.setLocation("Main Hall");
        task.setCritical(true);
        task.setHeadcount(5);
        task.setStatus("pending");
        task.setMandatorySkills(List.of("Audio"));
        task.setPreferredSkills(List.of("Lighting"));
        return taskRepository.save(task);
    }

    private void registerVolunteer(UUID eventId, User user) {
        Volunteer v = new Volunteer();
        v.setEventId(eventId);
        v.setUserId(user.getId());
        v.setFullName(user.getFullName());
        v.setEmail(user.getEmail());
        v.setRegisteredAt(Instant.now());
        volunteerRepository.save(v);
    }

    @Test
    void registeredVolunteerSeesTasksInExactOrder() throws Exception {
        Event event = createEvent("Task Order Event", "planning");
        registerVolunteer(event.getId(), volunteerA);

        Task t3 = createTask(event.getId(), "Task Day 2", LocalDate.of(2026, 12, 2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Task t1 = createTask(event.getId(), "Task Day 1 Early", LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        Task t2 = createTask(event.getId(), "Task Day 1 Later", LocalDate.of(2026, 12, 1), LocalTime.of(14, 0), LocalTime.of(15, 0));

        mockMvc.perform(get("/api/volunteer/events/" + event.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(t1.getId().toString()))
                .andExpect(jsonPath("$[0].title").value("Task Day 1 Early"))
                .andExpect(jsonPath("$[0].date").value("2026-12-01"))
                .andExpect(jsonPath("$[0].startTime").value("09:00"))
                .andExpect(jsonPath("$[0].endTime").value("10:00"))
                .andExpect(jsonPath("$[0].location").value("Main Hall"))
                .andExpect(jsonPath("$[0].critical").value(true))
                .andExpect(jsonPath("$[0].mandatorySkills[0]").value("Audio"))
                .andExpect(jsonPath("$[0].preferredSkills[0]").value("Lighting"))
                .andExpect(jsonPath("$[1].id").value(t2.getId().toString()))
                .andExpect(jsonPath("$[2].id").value(t3.getId().toString()));
    }

    @Test
    void responseHasNoHeadcountStatusEventId() throws Exception {
        Event event = createEvent("Task Key Set Event", "planning");
        registerVolunteer(event.getId(), volunteerA);
        createTask(event.getId(), "Task 1", LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));

        MvcResult result = mockMvc.perform(get("/api/volunteer/events/" + event.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isOk())
                .andReturn();

        List<?> list = objectMapper.readValue(result.getResponse().getContentAsString(), List.class);
        assertEquals(1, list.size());
        Map<?, ?> taskMap = (Map<?, ?>) list.get(0);

        Set<String> expectedKeys = Set.of(
                "id", "title", "description", "date", "startTime", "endTime",
                "location", "critical", "mandatorySkills", "preferredSkills"
        );
        assertEquals(expectedKeys, taskMap.keySet(), "Response must contain exact key set and nothing else");
    }

    @Test
    void unregisteredVolunteerGets404() throws Exception {
        Event event = createEvent("Unregistered Event", "planning");
        createTask(event.getId(), "Task 1", LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));

        mockMvc.perform(get("/api/volunteer/events/" + event.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerBToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found"));
    }

    @Test
    void draftEventGets404EvenIfRegistered() throws Exception {
        Event draftEvent = createEvent("Draft Event", "draft");
        createTask(draftEvent.getId(), "Task Draft", LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        registerVolunteer(draftEvent.getId(), volunteerA);

        mockMvc.perform(get("/api/volunteer/events/" + draftEvent.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found"));
    }

    @Test
    void missingEventGets404() throws Exception {
        UUID unknownEventId = UUID.randomUUID();
        mockMvc.perform(get("/api/volunteer/events/" + unknownEventId + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found"));
    }

    @Test
    void allThree404BodiesIdentical() throws Exception {
        UUID missingId = UUID.randomUUID();
        MvcResult missingRes = mockMvc.perform(get("/api/volunteer/events/" + missingId + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        Event draftEvent = createEvent("Draft Event 404", "draft");
        registerVolunteer(draftEvent.getId(), volunteerA);
        MvcResult draftRes = mockMvc.perform(get("/api/volunteer/events/" + draftEvent.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        Event unregEvent = createEvent("Unreg Event 404", "planning");
        MvcResult unregRes = mockMvc.perform(get("/api/volunteer/events/" + unregEvent.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals("{\"error\":\"Event not found\"}", missingRes.getResponse().getContentAsString());
        assertEquals("{\"error\":\"Event not found\"}", draftRes.getResponse().getContentAsString());
        assertEquals("{\"error\":\"Event not found\"}", unregRes.getResponse().getContentAsString());
    }

    @Test
    void otherEventsTasksNotLeaked() throws Exception {
        Event eventA = createEvent("Event A", "planning");
        Event eventB = createEvent("Event B", "planning");
        Task tA = createTask(eventA.getId(), "Task A", LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), LocalTime.of(10, 0));
        createTask(eventB.getId(), "Task B", LocalDate.of(2026, 12, 1), LocalTime.of(10, 0), LocalTime.of(11, 0));

        registerVolunteer(eventA.getId(), volunteerA);

        MvcResult result = mockMvc.perform(get("/api/volunteer/events/" + eventA.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tA.getId().toString()))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("Task B"));
    }

    @Test
    void emptyTaskListReturnsEmptyArray() throws Exception {
        Event event = createEvent("Empty Tasks Event", "planning");
        registerVolunteer(event.getId(), volunteerA);

        mockMvc.perform(get("/api/volunteer/events/" + event.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0))
                .andExpect(content().json("[]"));
    }

    @Test
    void organiserGets403() throws Exception {
        Event event = createEvent("Event Org Sec", "planning");
        mockMvc.perform(get("/api/volunteer/events/" + event.getId() + "/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        Event event = createEvent("Event Unauth Sec", "planning");
        mockMvc.perform(get("/api/volunteer/events/" + event.getId() + "/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
