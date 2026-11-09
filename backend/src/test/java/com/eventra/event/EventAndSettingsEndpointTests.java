package com.eventra.event;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.dto.CreateEventRequest;
import com.eventra.event.dto.UpdateEventRequest;
import com.eventra.settings.Settings;
import com.eventra.settings.SettingsRepository;
import com.eventra.settings.dto.SettingsRequest;
import com.eventra.settings.dto.WeightsDto;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class EventAndSettingsEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private EventRepository eventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

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

    private User organiser;
    private User volunteer;
    private String organiserToken;
    private String volunteerToken;

    private Settings initialSettingsSnapshot;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        organiser = createUser("organiser");
        volunteer = createUser("volunteer");
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());
        volunteerToken = jwtService.generateToken(volunteer.getId(), volunteer.getRole(), volunteer.getTokenVersion());

        // Snapshot existing settings or seed default
        Optional<Settings> existingSettings = settingsRepository.findById(true);
        if (existingSettings.isPresent()) {
            Settings cur = existingSettings.get();
            initialSettingsSnapshot = new Settings();
            initialSettingsSnapshot.setOnlyRow(true);
            initialSettingsSnapshot.setWeightSkill(cur.getWeightSkill());
            initialSettingsSnapshot.setWeightExperience(cur.getWeightExperience());
            initialSettingsSnapshot.setWeightPerformance(cur.getWeightPerformance());
            initialSettingsSnapshot.setWeightLoad(cur.getWeightLoad());
            initialSettingsSnapshot.setWeightInterest(cur.getWeightInterest());
            initialSettingsSnapshot.setFirstTimerBonus(cur.getFirstTimerBonus());
            initialSettingsSnapshot.setDefaultMaxHours(cur.getDefaultMaxHours());
        } else {
            Settings s = new Settings();
            s.setOnlyRow(true);
            s.setWeightSkill(20);
            s.setWeightExperience(20);
            s.setWeightPerformance(20);
            s.setWeightLoad(20);
            s.setWeightInterest(20);
            s.setFirstTimerBonus(5);
            s.setDefaultMaxHours(8);
            settingsRepository.save(s);
            initialSettingsSnapshot = s;
        }
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            volunteerRepository.findByEventId(eventId).forEach(v -> volunteerRepository.deleteById(v.getId()));
            taskRepository.findByEventId(eventId).forEach(t -> taskRepository.deleteById(t.getId()));
            if (eventRepository.existsById(eventId)) {
                eventRepository.deleteById(eventId);
            }
        }
        createdEventIds.clear();

        for (UUID userId : createdUserIds) {
            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
            }
        }
        createdUserIds.clear();

        // Restore settings snapshot
        if (initialSettingsSnapshot != null) {
            Settings s = settingsRepository.findById(true).orElse(new Settings());
            s.setOnlyRow(true);
            s.setWeightSkill(initialSettingsSnapshot.getWeightSkill());
            s.setWeightExperience(initialSettingsSnapshot.getWeightExperience());
            s.setWeightPerformance(initialSettingsSnapshot.getWeightPerformance());
            s.setWeightLoad(initialSettingsSnapshot.getWeightLoad());
            s.setWeightInterest(initialSettingsSnapshot.getWeightInterest());
            s.setFirstTimerBonus(initialSettingsSnapshot.getFirstTimerBonus());
            s.setDefaultMaxHours(initialSettingsSnapshot.getDefaultMaxHours());
            settingsRepository.save(s);
        }
    }

    private User createUser(String role) {
        User u = new User();
        String rand = UUID.randomUUID().toString().substring(0, 8);
        u.setEmail("user_" + rand + "@test.local");
        u.setFullName("User " + rand);
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode("secret"));
        u.setTokenVersion(0);
        u = userRepository.save(u);
        createdUserIds.add(u.getId());
        return u;
    }

    // -------------------------------------------------------------------------
    // 1. Organiser CRUD for events
    // -------------------------------------------------------------------------
    @Test
    void organiserCanCreateReadUpdateListAndDeleteEvent() throws Exception {
        // Create
        CreateEventRequest createReq = new CreateEventRequest();
        createReq.setName("Tech Symposium 2026");
        createReq.setDescription("Annual tech event");
        createReq.setStartDate(Instant.parse("2026-12-01T09:00:00Z"));
        createReq.setEndDate(Instant.parse("2026-12-03T18:00:00Z"));
        createReq.setStatus("draft");
        createReq.setCertificateTemplate(Map.of("type", "Standard"));

        MvcResult createRes = mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Tech Symposium 2026"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.createdBy").value(organiser.getId().toString()))
                .andExpect(jsonPath("$.certificateTemplate.type").value("Standard"))
                .andReturn();

        String eventIdStr = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();
        UUID eventId = UUID.fromString(eventIdStr);
        createdEventIds.add(eventId);

        // Read single
        mockMvc.perform(get("/api/events/" + eventId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventIdStr))
                .andExpect(jsonPath("$.name").value("Tech Symposium 2026"));

        // List
        mockMvc.perform(get("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // Update
        UpdateEventRequest updateReq = new UpdateEventRequest();
        updateReq.setName("Tech Symposium 2026 Updated");
        updateReq.setDescription("Updated description");
        updateReq.setStartDate(Instant.parse("2026-12-01T09:00:00Z"));
        updateReq.setEndDate(Instant.parse("2026-12-04T18:00:00Z"));
        updateReq.setStatus("live");
        updateReq.setCertificateTemplate(Map.of("type", "Custom"));

        mockMvc.perform(put("/api/events/" + eventId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tech Symposium 2026 Updated"))
                .andExpect(jsonPath("$.status").value("live"))
                .andExpect(jsonPath("$.certificateTemplate.type").value("Custom"));

        // Delete
        mockMvc.perform(delete("/api/events/" + eventId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isNoContent());

        assertFalse(eventRepository.existsById(eventId));
    }

    // -------------------------------------------------------------------------
    // 2. Authorization checks: Volunteer -> 403, Unauthenticated -> 401
    // -------------------------------------------------------------------------
    @Test
    void volunteerGets403OnAllEndpoints() throws Exception {
        UUID fakeId = UUID.randomUUID();

        // Events endpoints
        mockMvc.perform(get("/api/events")
                        .cookie(new Cookie("eventra_token", volunteerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Event\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(get("/api/events/" + fakeId)
                        .cookie(new Cookie("eventra_token", volunteerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(put("/api/events/" + fakeId)
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Event\",\"status\":\"draft\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(delete("/api/events/" + fakeId)
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        // Settings endpoints
        mockMvc.perform(get("/api/settings")
                        .cookie(new Cookie("eventra_token", volunteerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20},\"firstTimerBonus\":5,\"defaultMaxHours\":8}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void unauthenticatedGets401OnAllEndpoints() throws Exception {
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(post("/api/events")
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // -------------------------------------------------------------------------
    // 3. Write without X-Requested-With header is rejected
    // -------------------------------------------------------------------------
    @Test
    void writeWithoutCsrfHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));

        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // 4. Invalid input: blank name, name > 120, end before start, unknown field -> 400
    // -------------------------------------------------------------------------
    @Test
    void invalidEventInputsReturn400() throws Exception {
        // Blank name
        CreateEventRequest blankNameReq = new CreateEventRequest();
        blankNameReq.setName("   ");
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankNameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // Name > 120
        CreateEventRequest longNameReq = new CreateEventRequest();
        longNameReq.setName("A".repeat(121));
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longNameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // End before start
        CreateEventRequest invalidDatesReq = new CreateEventRequest();
        invalidDatesReq.setName("Valid Name");
        invalidDatesReq.setStartDate(Instant.parse("2026-12-10T10:00:00Z"));
        invalidDatesReq.setEndDate(Instant.parse("2026-12-05T10:00:00Z"));
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDatesReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("End date must not be before start date."));

        // Unknown JSON field -> fixed message "Unknown field"
        String unknownFieldJson = "{\"name\":\"Valid Event\",\"unknownField\":\"oops\"}";
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownFieldJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));
    }

    // -------------------------------------------------------------------------
    // 5. Unknown event id -> 404; deleting an event with tasks or volunteers -> 409
    // -------------------------------------------------------------------------
    @Test
    void unknownEventIdReturns404() throws Exception {
        UUID fakeId = UUID.randomUUID();
        mockMvc.perform(get("/api/events/" + fakeId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(delete("/api/events/" + fakeId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void deleteEventWithTasksOrVolunteersReturns409() throws Exception {
        // Event with Task
        Event event1 = new Event();
        event1.setName("Event with Task");
        event1.setCreatedBy(organiser.getId());
        event1.setStatus("draft");
        event1 = eventRepository.save(event1);
        createdEventIds.add(event1.getId());

        Task task = new Task();
        task.setEventId(event1.getId());
        task.setTitle("Setup sound");
        task.setTaskDate(LocalDate.now());
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(2);
        taskRepository.save(task);

        mockMvc.perform(delete("/api/events/" + event1.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot delete event: it has 1 task(s) and 0 volunteer(s)."));

        // Event with Volunteer
        Event event2 = new Event();
        event2.setName("Event with Volunteer");
        event2.setCreatedBy(organiser.getId());
        event2.setStatus("draft");
        event2 = eventRepository.save(event2);
        createdEventIds.add(event2.getId());

        Volunteer vol = new Volunteer();
        vol.setEventId(event2.getId());
        vol.setFullName("John Doe");
        vol.setEmail("john.doe@test.local");
        vol.setPhone("1234567890");
        vol.setDepartment("CS");
        vol.setYearOfStudy(2);
        vol.setStatus("registered");
        volunteerRepository.save(vol);

        mockMvc.perform(delete("/api/events/" + event2.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot delete event: it has 0 task(s) and 1 volunteer(s)."));
    }

    // -------------------------------------------------------------------------
    // 6. Settings: valid update works, sum 99 -> 400, sum 101 -> 400, negative -> 400, 1 row preserved
    // -------------------------------------------------------------------------
    @Test
    void settingsValidationAndSingleRowPreserved() throws Exception {
        // GET settings
        mockMvc.perform(get("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weights.skill").isNumber())
                .andExpect(jsonPath("$.firstTimerBonus").isNumber())
                .andExpect(jsonPath("$.defaultMaxHours").isNumber());

        // Valid update
        SettingsRequest validReq = new SettingsRequest(
                new WeightsDto(30, 20, 20, 15, 15),
                10,
                12
        );
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weights.skill").value(30))
                .andExpect(jsonPath("$.weights.interest").value(15))
                .andExpect(jsonPath("$.firstTimerBonus").value(10))
                .andExpect(jsonPath("$.defaultMaxHours").value(12));

        assertEquals(1, settingsRepository.count(), "Settings table must have exactly one row");

        // Sum 99 -> 400
        SettingsRequest sum99Req = new SettingsRequest(
                new WeightsDto(29, 20, 20, 15, 15),
                10,
                12
        );
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sum99Req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Sum of weights must be exactly 100, but got: 99"));

        // Sum 101 -> 400
        SettingsRequest sum101Req = new SettingsRequest(
                new WeightsDto(31, 20, 20, 15, 15),
                10,
                12
        );
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sum101Req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Sum of weights must be exactly 100, but got: 101"));

        // Negative weight -> 400
        SettingsRequest negativeReq = new SettingsRequest(
                new WeightsDto(-5, 25, 20, 30, 30),
                10,
                12
        );
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        assertEquals(1, settingsRepository.count(), "Settings table must still have exactly one row");
    }

    // -------------------------------------------------------------------------
    // 7. No endpoint response contains a stack trace
    // -------------------------------------------------------------------------
    @Test
    void noEndpointResponseContainsStackTrace() throws Exception {
        MvcResult res400 = mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid_json}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body400 = res400.getResponse().getContentAsString();
        assertFalse(body400.contains("Exception"), "Body should not contain raw Exception names");
        assertFalse(body400.contains("at com.eventra"), "Body should not contain stack traces");

        MvcResult res404 = mockMvc.perform(get("/api/events/" + UUID.randomUUID())
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andReturn();

        String body404 = res404.getResponse().getContentAsString();
        assertFalse(body404.contains("Exception"));
        assertFalse(body404.contains("at com.eventra"));
    }

    // -------------------------------------------------------------------------
    // 8. New Tests: Weight overflow & > 100
    // -------------------------------------------------------------------------
    @Test
    void weightOverflowAndOver100Return400() throws Exception {
        // Weight 101 -> 400
        String weight101Json = "{\"weights\":{\"skill\":101,\"experience\":0,\"performance\":0,\"load\":0,\"interest\":0},\"firstTimerBonus\":5,\"defaultMaxHours\":8}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weight101Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // Overflow: two weights of 2147483647 plus one of 102
        // Integer overflow: 2147483647 + 2147483647 + 102 = 100 in 32-bit signed int arithmetic
        String overflowJson = "{\"weights\":{\"skill\":2147483647,\"experience\":2147483647,\"performance\":102,\"load\":0,\"interest\":0},\"firstTimerBonus\":5,\"defaultMaxHours\":8}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overflowJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // -------------------------------------------------------------------------
    // 9. New Tests: firstTimerBonus and defaultMaxHours validation
    // -------------------------------------------------------------------------
    @Test
    void firstTimerBonusAndDefaultMaxHoursBounds() throws Exception {
        // null firstTimerBonus -> 400
        String nullBonusJson = "{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20},\"firstTimerBonus\":null,\"defaultMaxHours\":8}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nullBonusJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // firstTimerBonus 21 -> 400
        String bonus21Json = "{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20},\"firstTimerBonus\":21,\"defaultMaxHours\":8}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bonus21Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // defaultMaxHours 0 -> 400
        String hours0Json = "{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20},\"firstTimerBonus\":5,\"defaultMaxHours\":0}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hours0Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // defaultMaxHours 25 -> 400
        String hours25Json = "{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20},\"firstTimerBonus\":5,\"defaultMaxHours\":25}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hours25Json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    // -------------------------------------------------------------------------
    // 10. New Tests: Unknown field on settings and on weights -> 400 {"error":"Unknown field"}
    // -------------------------------------------------------------------------
    @Test
    void unknownFieldOnSettingsAndWeightsReturns400() throws Exception {
        String unknownSettingsJson = "{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20},\"firstTimerBonus\":5,\"defaultMaxHours\":8,\"extraProp\":\"bad\"}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownSettingsJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        String unknownWeightsJson = "{\"weights\":{\"skill\":20,\"experience\":20,\"performance\":20,\"load\":20,\"interest\":20,\"foo\":\"bar\"},\"firstTimerBonus\":5,\"defaultMaxHours\":8}";
        mockMvc.perform(put("/api/settings")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownWeightsJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));
    }

    // -------------------------------------------------------------------------
    // 11. New Tests: Bad UUID in path -> 400 with {"error":...}
    // -------------------------------------------------------------------------
    @Test
    void badUuidInPathReturns400() throws Exception {
        mockMvc.perform(get("/api/events/not-a-valid-uuid")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid parameter"));
    }

    // -------------------------------------------------------------------------
    // 12. New Tests: Unsupported HTTP method -> 405
    // -------------------------------------------------------------------------
    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").exists());
    }

    // -------------------------------------------------------------------------
    // 13. New Tests: Event status validation
    // -------------------------------------------------------------------------
    @Test
    void eventStatusValidation() throws Exception {
        // Bad event status on create -> 400
        CreateEventRequest badStatusReq = new CreateEventRequest();
        badStatusReq.setName("Bad Status Event");
        badStatusReq.setStatus("invalid_status");
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badStatusReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status"));

        // POST without status -> created with status "draft"
        CreateEventRequest noStatusReq = new CreateEventRequest();
        noStatusReq.setName("Default Status Event");
        MvcResult res = mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noStatusReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andReturn();

        String createdIdStr = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        UUID createdId = UUID.fromString(createdIdStr);
        createdEventIds.add(createdId);

        // PUT without status -> 400
        String noStatusPutJson = "{\"name\":\"Updated Event\"}";
        mockMvc.perform(put("/api/events/" + createdId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noStatusPutJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // PUT with bad status -> 400
        UpdateEventRequest badStatusPut = new UpdateEventRequest();
        badStatusPut.setName("Updated Event");
        badStatusPut.setStatus("bogus");
        mockMvc.perform(put("/api/events/" + createdId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badStatusPut)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status"));
    }

    // -------------------------------------------------------------------------
    // 14. New Tests: certificateTemplate validation & absent keeps stored
    // -------------------------------------------------------------------------
    @Test
    void certificateTemplateValidationAndAbsentPreserved() throws Exception {
        // Disallowed key in certificateTemplate -> 400
        CreateEventRequest badKeyReq = new CreateEventRequest();
        badKeyReq.setName("Bad Cert Key Event");
        badKeyReq.setCertificateTemplate(Map.of("disallowedKey", "value"));
        mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badKeyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Disallowed key in certificateTemplate: disallowedKey"));

        // Create with valid template
        CreateEventRequest validCertReq = new CreateEventRequest();
        validCertReq.setName("Cert Event");
        validCertReq.setCertificateTemplate(Map.of("type", "Gold", "templateId", "tpl-123"));
        MvcResult res = mockMvc.perform(post("/api/events")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCertReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.certificateTemplate.type").value("Gold"))
                .andReturn();

        UUID eventId = UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        createdEventIds.add(eventId);

        // PUT with certificateTemplate absent -> keeps stored template
        String updateWithoutCertJson = "{\"name\":\"Cert Event Updated\",\"status\":\"planning\"}";
        mockMvc.perform(put("/api/events/" + eventId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithoutCertJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cert Event Updated"))
                .andExpect(jsonPath("$.status").value("planning"))
                .andExpect(jsonPath("$.certificateTemplate.type").value("Gold"))
                .andExpect(jsonPath("$.certificateTemplate.templateId").value("tpl-123"));
    }

    // -------------------------------------------------------------------------
    // 15. New Tests: Forced DataIntegrityViolationException on delete -> 409
    //                and verify findWithLockById was used
    // -------------------------------------------------------------------------
    @Test
    void deleteUsesLockedLookupAndTranslatesDataIntegrityViolation() throws Exception {
        Event event = new Event();
        event.setName("Delete Test Event");
        event.setStatus("draft");
        event.setCreatedBy(organiser.getId());
        event = eventRepository.save(event);
        createdEventIds.add(event.getId());

        // Verify locked lookup is called during normal delete
        mockMvc.perform(delete("/api/events/" + event.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isNoContent());

        verify(eventRepository, atLeastOnce()).findWithLockById(event.getId());

        // Re-create event and force DataIntegrityViolationException on delete
        Event event2 = new Event();
        event2.setName("Delete Error Event");
        event2.setStatus("draft");
        event2.setCreatedBy(organiser.getId());
        Event savedEvent2 = eventRepository.save(event2);
        final UUID event2Id = savedEvent2.getId();
        createdEventIds.add(event2Id);

        doThrow(new DataIntegrityViolationException("FK constraint violation"))
                .when(eventRepository).delete(argThat(e -> e != null && event2Id.equals(e.getId())));

        mockMvc.perform(delete("/api/events/" + event2Id)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header("X-Requested-With", "EvenTra"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot delete event: conflict with existing data"));
    }
}
