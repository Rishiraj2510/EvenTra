package com.eventra;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.settings.Settings;
import com.eventra.settings.SettingsRepository;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.task.dto.CreateTaskRequest;
import com.eventra.task.dto.UpdateTaskRequest;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import com.eventra.volunteer.dto.UpdateVolunteerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class TaskAndVolunteerEndpointTests {

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
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up created events (cascades to tasks and volunteers)
        for (UUID eventId : createdEventIds) {
            try {
                eventRepository.deleteById(eventId);
            } catch (Exception ignored) {
            }
        }
        createdEventIds.clear();

        // Clean up users
        for (UUID userId : createdUserIds) {
            try {
                userRepository.deleteById(userId);
            } catch (Exception ignored) {
            }
        }
        createdUserIds.clear();

        // Restore settings if altered
        if (initialSettingsSnapshot != null) {
            settingsRepository.save(initialSettingsSnapshot);
        }
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

    private Event createEvent(String name) {
        Event event = new Event();
        event.setName(name);
        event.setDescription("Test Description");
        event.setStartDate(Instant.now().plusSeconds(3600));
        event.setEndDate(Instant.now().plusSeconds(7200));
        event.setStatus("draft");
        event.setCreatedBy(organiser.getId());
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    // =========================================================================
    // TASKS TESTS
    // =========================================================================

    @Test
    void organiserCanCreateReadUpdateListAndDeleteTasksWithEventIsolation() throws Exception {
        Event event1 = createEvent("Event 1");
        Event event2 = createEvent("Event 2");

        // 1. Create task in event1
        CreateTaskRequest req1 = new CreateTaskRequest();
        req1.setEventId(event1.getId());
        req1.setTitle("Stage Ops");
        req1.setDescription("Manage stage equipment");
        req1.setDate(LocalDate.of(2026, 12, 1));
        req1.setStartTime(LocalTime.of(10, 0));
        req1.setEndTime(LocalTime.of(12, 0));
        req1.setHeadcount(3);
        req1.setMandatorySkills(List.of("Audio Board Operation"));
        req1.setPreferredSkills(List.of("Crowd Flow Control"));
        req1.setCritical(true);
        req1.setLocation("Auditorium Main Stage");

        MvcResult createRes = mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.eventId").value(event1.getId().toString()))
                .andExpect(jsonPath("$.title").value("Stage Ops"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.critical").value(true))
                .andReturn();

        String taskIdStr = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();
        UUID taskId = UUID.fromString(taskIdStr);

        // Create task in event2
        CreateTaskRequest req2 = new CreateTaskRequest();
        req2.setEventId(event2.getId());
        req2.setTitle("Badging Desk");
        req2.setDate(LocalDate.of(2026, 12, 1));
        req2.setStartTime(LocalTime.of(9, 0));
        req2.setEndTime(LocalTime.of(11, 0));
        req2.setHeadcount(2);
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isCreated());

        // 2. Read task
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskIdStr))
                .andExpect(jsonPath("$.title").value("Stage Ops"));

        // 3. List tasks for event1 -> returns only task1
        mockMvc.perform(get("/api/tasks?eventId=" + event1.getId())
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskIdStr))
                .andExpect(jsonPath("$[0].title").value("Stage Ops"));

        // List tasks for event2 -> returns only task2
        mockMvc.perform(get("/api/tasks?eventId=" + event2.getId())
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Badging Desk"));

        // 4. Update task (status required)
        UpdateTaskRequest updateReq = new UpdateTaskRequest();
        updateReq.setTitle("Stage Ops Updated");
        updateReq.setDate(LocalDate.of(2026, 12, 2));
        updateReq.setStartTime(LocalTime.of(11, 0));
        updateReq.setEndTime(LocalTime.of(13, 0));
        updateReq.setStatus("in-progress");
        updateReq.setHeadcount(4);
        updateReq.setCritical(false);

        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Stage Ops Updated"))
                .andExpect(jsonPath("$.status").value("in-progress"))
                .andExpect(jsonPath("$.headcount").value(4));

        // 5. Delete task
        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNoContent());

        // 6. Verify row is deleted
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found: " + taskId));
    }

    @Test
    void taskValidation400Errors() throws Exception {
        Event event = createEvent("Validation Event");

        // Helper to perform POST
        // 1. Blank title
        CreateTaskRequest req = baseTaskRequest(event.getId());
        req.setTitle("   ");
        expectBadRequestPostTask(req);

        // 2. Title over 120 chars
        req = baseTaskRequest(event.getId());
        req.setTitle("T".repeat(121));
        expectBadRequestPostTask(req);

        // 3. End equal to start
        req = baseTaskRequest(event.getId());
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(10, 0));
        expectBadRequestPostTask(req);

        // 4. End before start
        req = baseTaskRequest(event.getId());
        req.setStartTime(LocalTime.of(11, 0));
        req.setEndTime(LocalTime.of(10, 0));
        expectBadRequestPostTask(req);

        // 5. Headcount 0
        req = baseTaskRequest(event.getId());
        req.setHeadcount(0);
        expectBadRequestPostTask(req);

        // 6. Headcount 101
        req = baseTaskRequest(event.getId());
        req.setHeadcount(101);
        expectBadRequestPostTask(req);

        // 7. 21 skills
        req = baseTaskRequest(event.getId());
        List<String> twentyOneSkills = new ArrayList<>();
        for (int i = 1; i <= 21; i++) {
            twentyOneSkills.add("Skill " + i);
        }
        req.setMandatorySkills(twentyOneSkills);
        expectBadRequestPostTask(req);

        // 8. Skill of 41 chars
        req = baseTaskRequest(event.getId());
        req.setMandatorySkills(List.of("S".repeat(41)));
        expectBadRequestPostTask(req);

        // 9. Same skill in both lists
        req = baseTaskRequest(event.getId());
        req.setMandatorySkills(List.of("Audio Board"));
        req.setPreferredSkills(List.of("audio board"));
        expectBadRequestPostTask(req);

        // 10. Bad status on create
        req = baseTaskRequest(event.getId());
        req.setStatus("invalid_status");
        expectBadRequestPostTask(req);

        // 11. Missing eventId on list
        mockMvc.perform(get("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 12. Malformed eventId on list
        mockMvc.perform(get("/api/tasks?eventId=not-a-uuid")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid parameter"));
    }

    @Test
    void duplicateSkillsCollapseToFirstSpelling() throws Exception {
        Event event = createEvent("Skill Collapse Event");
        CreateTaskRequest req = baseTaskRequest(event.getId());
        req.setMandatorySkills(List.of("Java", "java", "JAVA"));

        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertEquals("[\"Java\"]", objectMapper.readTree(body).get("mandatorySkills").toString());
    }

    @Test
    void taskStatusTransitionsAndRejections() throws Exception {
        Event event = createEvent("Status Transition Event");

        // Create with default status -> pending
        CreateTaskRequest createReq = baseTaskRequest(event.getId());
        createReq.setStatus(null);
        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();

        String taskId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        // PUT with in-progress -> accepted
        UpdateTaskRequest putReq = baseUpdateTaskRequest();
        putReq.setStatus("in-progress");
        putReq.setCritical(false);
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in-progress"));

        // PUT with completed -> accepted
        putReq.setStatus("completed");
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        // PUT with pending -> accepted
        putReq.setStatus("pending");
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));

        // PUT with done -> 400
        putReq.setStatus("done");
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status"));

        // PUT with in_progress -> 400
        putReq.setStatus("in_progress");
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status"));
    }

    @Test
    void taskNotFoundAndUnknownFieldValidation() throws Exception {
        UUID unknownEventId = UUID.randomUUID();
        CreateTaskRequest createReq = baseTaskRequest(unknownEventId);

        // Unknown eventId on create -> 404
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found: " + unknownEventId));

        // Unknown eventId on list -> 404
        mockMvc.perform(get("/api/tasks?eventId=" + unknownEventId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found: " + unknownEventId));

        // Unknown task id on get/put/delete -> 404
        UUID unknownTaskId = UUID.randomUUID();
        mockMvc.perform(get("/api/tasks/" + unknownTaskId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Task not found: " + unknownTaskId));

        // PUT with eventId field -> 400 (unknown field)
        Event event = createEvent("Put Unknown Field Event");
        CreateTaskRequest t = baseTaskRequest(event.getId());
        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(t)))
                .andExpect(status().isCreated())
                .andReturn();
        String validTaskId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        Map<String, Object> putMap = new HashMap<>();
        putMap.put("title", "Updated");
        putMap.put("date", "2026-12-01");
        putMap.put("startTime", "10:00");
        putMap.put("endTime", "12:00");
        putMap.put("headcount", 2);
        putMap.put("critical", false);
        putMap.put("status", "pending");
        putMap.put("eventId", UUID.randomUUID().toString()); // illegal field

        mockMvc.perform(put("/api/tasks/" + validTaskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(putMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));
    }

    // =========================================================================
    // VOLUNTEERS TESTS
    // =========================================================================

    @Test
    void volunteerUpdateAllowedFieldsAndRejections() throws Exception {
        Event event = createEvent("Volunteer Event");
        Volunteer v = new Volunteer();
        v.setEventId(event.getId());
        v.setFullName("Original Name");
        v.setEmail("orig@college.edu");
        v.setPhone("+91 98765 43210");
        v.setDepartment("Computer Science");
        v.setYearOfStudy(3);
        v.setSkills(List.of(new SkillLevel("Coding", "Advanced")));
        v.setPastEventsCount(2);
        v.setAvailability(List.of(new AvailabilitySlot("Monday", "Morning")));
        v.setMaxHoursPerEvent(8);
        v.setInterests(List.of("Technology"));
        v.setNotificationPreference("email");
        v.setStatus("active");
        v.setExtraAnswers(Map.of("tshirt", "M"));
        v.setRegisteredAt(Instant.now());
        Volunteer saved = volunteerRepository.save(v);

        // 1. Update allowed fields
        UpdateVolunteerRequest updateReq = new UpdateVolunteerRequest();
        updateReq.setFullName("Updated Volunteer");
        updateReq.setPhone("+1 (555) 123-4567");
        updateReq.setDepartment("Information Technology");
        updateReq.setYearOfStudy(4);
        updateReq.setSkills(List.of(new SkillLevel("Robotics", "Intermediate")));
        updateReq.setPastEventsCount(3);
        updateReq.setAvailability(List.of(new AvailabilitySlot("Tuesday", "Afternoon")));
        updateReq.setMaxHoursPerEvent(12);
        updateReq.setInterests(List.of("Robotics", "AI"));
        updateReq.setNotificationPreference("whatsapp");
        updateReq.setStatus("inactive");

        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().toString()))
                .andExpect(jsonPath("$.fullName").value("Updated Volunteer"))
                .andExpect(jsonPath("$.email").value("orig@college.edu")) // preserved
                .andExpect(jsonPath("$.phone").value("+1 (555) 123-4567"))
                .andExpect(jsonPath("$.yearOfStudy").value(4))
                .andExpect(jsonPath("$.pastEventsCount").value(3))
                .andExpect(jsonPath("$.maxHoursPerEvent").value(12))
                .andExpect(jsonPath("$.notificationPreference").value("whatsapp"))
                .andExpect(jsonPath("$.status").value("inactive"))
                .andExpect(jsonPath("$.extraAnswers.tshirt").value("M")); // preserved

        // 2. PUT with email -> 400
        Map<String, Object> badEmailMap = Map.of("email", "hacker@evil.com");
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badEmailMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        // PUT with eventId -> 400
        Map<String, Object> badEventIdMap = Map.of("eventId", UUID.randomUUID().toString());
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badEventIdMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        // PUT with userId -> 400
        Map<String, Object> badUserIdMap = Map.of("userId", UUID.randomUUID().toString());
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badUserIdMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        // 3. Bad phone (invalid chars) -> 400
        UpdateVolunteerRequest badPhoneReq = new UpdateVolunteerRequest();
        badPhoneReq.setPhone("12345abcde");
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPhoneReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // 4. yearOfStudy 0 and 7 -> 400
        UpdateVolunteerRequest badYear0 = new UpdateVolunteerRequest();
        badYear0.setYearOfStudy(0);
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badYear0)))
                .andExpect(status().isBadRequest());

        UpdateVolunteerRequest badYear7 = new UpdateVolunteerRequest();
        badYear7.setYearOfStudy(7);
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badYear7)))
                .andExpect(status().isBadRequest());

        // 5. maxHoursPerEvent 0 and 25 -> 400
        UpdateVolunteerRequest badHours0 = new UpdateVolunteerRequest();
        badHours0.setMaxHoursPerEvent(0);
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badHours0)))
                .andExpect(status().isBadRequest());

        UpdateVolunteerRequest badHours25 = new UpdateVolunteerRequest();
        badHours25.setMaxHoursPerEvent(25);
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badHours25)))
                .andExpect(status().isBadRequest());

        // 6. Invalid skill level -> 400
        UpdateVolunteerRequest badSkillLevel = new UpdateVolunteerRequest();
        badSkillLevel.setSkills(List.of(new SkillLevel("Coding", "Master")));
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSkillLevel)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid skill level"));

        // 7. Duplicate availability pair -> 400
        UpdateVolunteerRequest dupAvail = new UpdateVolunteerRequest();
        dupAvail.setAvailability(List.of(
                new AvailabilitySlot("Monday", "Morning"),
                new AvailabilitySlot("monday", "morning")
        ));
        mockMvc.perform(put("/api/volunteers/" + saved.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupAvail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void volunteersListScopedByEventIdAndIsolationAndDelete() throws Exception {
        Event event1 = createEvent("Event Alpha");
        Event event2 = createEvent("Event Beta");

        Volunteer v1 = new Volunteer();
        v1.setEventId(event1.getId());
        v1.setFullName("Volunteer Alpha");
        v1.setEmail("alpha@college.edu");
        v1.setRegisteredAt(Instant.now());
        v1 = volunteerRepository.save(v1);

        Volunteer v2 = new Volunteer();
        v2.setEventId(event2.getId());
        v2.setFullName("Volunteer Beta");
        v2.setEmail("beta@college.edu");
        v2.setRegisteredAt(Instant.now());
        v2 = volunteerRepository.save(v2);

        // List event1 -> only alpha
        mockMvc.perform(get("/api/volunteers?eventId=" + event1.getId())
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(v1.getId().toString()))
                .andExpect(jsonPath("$[0].fullName").value("Volunteer Alpha"));

        // List event2 -> only beta
        mockMvc.perform(get("/api/volunteers?eventId=" + event2.getId())
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(v2.getId().toString()))
                .andExpect(jsonPath("$[0].fullName").value("Volunteer Beta"));

        // Unknown volunteer id -> 404
        UUID unknownVolId = UUID.randomUUID();
        mockMvc.perform(get("/api/volunteers/" + unknownVolId)
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Volunteer not found: " + unknownVolId));

        // Delete v1 -> 204
        mockMvc.perform(delete("/api/volunteers/" + v1.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNoContent());

        // Verify v1 is gone
        mockMvc.perform(get("/api/volunteers/" + v1.getId())
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // SECURITY & ACCESS CONTROL TESTS
    // =========================================================================

    @Test
    void volunteerRoleGets403OnAllEndpointsNoCookieGets401() throws Exception {
        Event event = createEvent("Security Test Event");
        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle("Sec Task");
        task.setTaskDate(LocalDate.now());
        task.setStartTime(LocalTime.of(10, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task = taskRepository.save(task);

        Volunteer vol = new Volunteer();
        vol.setEventId(event.getId());
        vol.setFullName("Sec Vol");
        vol.setEmail("secvol@college.edu");
        vol.setRegisteredAt(Instant.now());
        vol = volunteerRepository.save(vol);

        List<String> getUrls = List.of(
                "/api/tasks?eventId=" + event.getId(),
                "/api/tasks/" + task.getId(),
                "/api/volunteers?eventId=" + event.getId(),
                "/api/volunteers/" + vol.getId()
        );

        // 1. Volunteer role gets 403 on GET
        for (String url : getUrls) {
            mockMvc.perform(get(url)
                            .cookie(new Cookie("eventra_token", volunteerToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        // 2. No cookie gets 401 on GET
        for (String url : getUrls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        // 3. Write requests with volunteer cookie get 403
        CreateTaskRequest dummyCreate = baseTaskRequest(event.getId());
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dummyCreate)))
                .andExpect(status().isForbidden());

        UpdateTaskRequest dummyUpdate = baseUpdateTaskRequest();
        mockMvc.perform(put("/api/tasks/" + task.getId())
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dummyUpdate)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tasks/" + task.getId())
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());

        UpdateVolunteerRequest dummyVolUpdate = new UpdateVolunteerRequest();
        dummyVolUpdate.setFullName("Valid Vol");
        mockMvc.perform(put("/api/volunteers/" + vol.getId())
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dummyVolUpdate)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/volunteers/" + vol.getId())
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());

        // 4. Write requests without CSRF header get 403
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));
    }

    @Test
    void noResponseBodyContainsExceptionOrStackTrace() throws Exception {
        // Trigger a bad request error
        MvcResult res = mockMvc.perform(get("/api/tasks?eventId=not-a-valid-uuid")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isBadRequest())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertFalse(body.contains("Exception"), "Response must not contain 'Exception'");
        assertFalse(body.contains("at com.eventra"), "Response must not contain stack trace");
    }

    // =========================================================================
    // NEW HARDENING TESTS
    // =========================================================================

    @Test
    void newHardeningTestsForPutFieldsAndTextRules() throws Exception {
        Event event = createEvent("Hardening Event");
        CreateTaskRequest createReq = baseTaskRequest(event.getId());
        MvcResult res = mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

        // 1. PUT task without headcount -> 400
        UpdateTaskRequest noHeadcount = baseUpdateTaskRequest();
        noHeadcount.setHeadcount(null);
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noHeadcount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("headcount is required"));

        // 2. PUT task without critical -> 400
        UpdateTaskRequest noCritical = baseUpdateTaskRequest();
        noCritical.setCritical(null);
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noCritical)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("critical is required"));

        // 3. title containing \u0000 -> 400
        CreateTaskRequest nulTitle = baseTaskRequest(event.getId());
        nulTitle.setTitle("Task\u0000Bad");
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nulTitle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("title contains invalid characters"));

        // 4. location containing a tab -> 400
        CreateTaskRequest tabLocation = baseTaskRequest(event.getId());
        tabLocation.setLocation("Room\t101");
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tabLocation)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("location contains invalid characters"));

        // 5. description containing "\n" -> 201 on create and 200 on update
        CreateTaskRequest newlineDesc = baseTaskRequest(event.getId());
        newlineDesc.setDescription("Line 1\nLine 2\r\nLine 3\tIndented");
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newlineDesc)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Line 1\nLine 2\r\nLine 3\tIndented"));

        UpdateTaskRequest updateNewlineDesc = baseUpdateTaskRequest();
        updateNewlineDesc.setDescription("Updated\nLine\tTab");
        mockMvc.perform(put("/api/tasks/" + taskId)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateNewlineDesc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated\nLine\tTab"));

        // 6. description containing \u0000 -> 400
        CreateTaskRequest nulDesc = baseTaskRequest(event.getId());
        nulDesc.setDescription("Hello\u0000World");
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nulDesc)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("description contains invalid characters"));

        // 7. invalid skill level response body must not contain the string that was sent
        Volunteer v = new Volunteer();
        v.setEventId(event.getId());
        v.setFullName("Hardening Vol");
        v.setEmail("hard@college.edu");
        v.setRegisteredAt(Instant.now());
        v = volunteerRepository.save(v);

        UpdateVolunteerRequest badSkillSent = new UpdateVolunteerRequest();
        badSkillSent.setSkills(List.of(new SkillLevel("Coding", "SuperSecretLevel123")));
        MvcResult skillRes = mockMvc.perform(put("/api/volunteers/" + v.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSkillSent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid skill level"))
                .andReturn();
        assertFalse(skillRes.getResponse().getContentAsString().contains("SuperSecretLevel123"),
                "Response must not echo the sent invalid skill level");

        // 8. volunteer department with control char -> 400
        UpdateVolunteerRequest badDept = new UpdateVolunteerRequest();
        badDept.setDepartment("Computer\u0007Science");
        mockMvc.perform(put("/api/volunteers/" + v.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDept)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("department contains invalid characters"));

        // volunteer interest with control char -> 400
        UpdateVolunteerRequest badInterest = new UpdateVolunteerRequest();
        badInterest.setInterests(List.of("Music\u0000Evil"));
        mockMvc.perform(put("/api/volunteers/" + v.getId())
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badInterest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("interests contains invalid characters"));
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private CreateTaskRequest baseTaskRequest(UUID eventId) {
        CreateTaskRequest req = new CreateTaskRequest();
        req.setEventId(eventId);
        req.setTitle("Valid Task Title");
        req.setDescription("Description");
        req.setDate(LocalDate.of(2026, 12, 1));
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(12, 0));
        req.setHeadcount(2);
        return req;
    }

    private UpdateTaskRequest baseUpdateTaskRequest() {
        UpdateTaskRequest req = new UpdateTaskRequest();
        req.setTitle("Valid Update Task Title");
        req.setDate(LocalDate.of(2026, 12, 1));
        req.setStartTime(LocalTime.of(10, 0));
        req.setEndTime(LocalTime.of(12, 0));
        req.setHeadcount(2);
        req.setCritical(false);
        req.setStatus("pending");
        return req;
    }

    private void expectBadRequestPostTask(CreateTaskRequest req) throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
