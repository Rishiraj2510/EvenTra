package com.eventra.registration;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.registration.dto.ExtraQuestionDto;
import com.eventra.registration.dto.UpdateRegistrationFormRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class RegistrationFormEndpointTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationFormRepository registrationFormRepository;

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
    private User volunteer;
    private String organiserToken;
    private String volunteerToken;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        organiser = createUser("organiser");
        volunteer = createUser("volunteer");
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());
        volunteerToken = jwtService.generateToken(volunteer.getId(), volunteer.getRole(), volunteer.getTokenVersion());
    }

    @AfterEach
    void tearDown() {
        // Cleaning up events cascades to registration_forms and volunteers
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

    // -------------------------------------------------------------------------
    // 1. Default GET shape (and no row written)
    // -------------------------------------------------------------------------
    @Test
    void defaultGetShapeAndNoRowWritten() throws Exception {
        Event event = createEvent("Default Shape Event");

        // Verify no row exists initially
        assertTrue(registrationFormRepository.findByEventId(event.getId()).isEmpty());

        mockMvc.perform(get("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.opensAt").doesNotExist())
                .andExpect(jsonPath("$.closesAt").doesNotExist())
                .andExpect(jsonPath("$.maxResponses").doesNotExist())
                .andExpect(jsonPath("$.offeredSkills").isArray())
                .andExpect(jsonPath("$.offeredSkills.length()").value(0))
                .andExpect(jsonPath("$.extraQuestions").isArray())
                .andExpect(jsonPath("$.extraQuestions.length()").value(0))
                .andExpect(jsonPath("$.responseCount").value(0));

        // Still no row written in DB
        assertTrue(registrationFormRepository.findByEventId(event.getId()).isEmpty());
    }

    // -------------------------------------------------------------------------
    // 2. PUT then GET round trip
    // -------------------------------------------------------------------------
    @Test
    void putThenGetRoundTrip() throws Exception {
        Event event = createEvent("Round Trip Event");

        Instant opensAt = Instant.parse("2026-12-01T10:00:00Z");
        Instant closesAt = Instant.parse("2026-12-05T18:00:00Z");

        UpdateRegistrationFormRequest req = new UpdateRegistrationFormRequest();
        req.setOpensAt(opensAt);
        req.setClosesAt(closesAt);
        req.setMaxResponses(50);
        req.setOfferedSkills(List.of("Audio Board Operation", "First Aid Certified"));
        req.setExtraQuestions(List.of(
                new ExtraQuestionDto("tshirt_size", "T-shirt Size"),
                new ExtraQuestionDto("emergency_contact", "Emergency Contact")
        ));

        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.maxResponses").value(50))
                .andExpect(jsonPath("$.offeredSkills.length()").value(2))
                .andExpect(jsonPath("$.extraQuestions.length()").value(2));

        // GET verification
        mockMvc.perform(get("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.opensAt").value("2026-12-01T10:00:00Z"))
                .andExpect(jsonPath("$.closesAt").value("2026-12-05T18:00:00Z"))
                .andExpect(jsonPath("$.maxResponses").value(50))
                .andExpect(jsonPath("$.offeredSkills[0]").value("Audio Board Operation"))
                .andExpect(jsonPath("$.offeredSkills[1]").value("First Aid Certified"))
                .andExpect(jsonPath("$.extraQuestions[0].id").value("tshirt_size"))
                .andExpect(jsonPath("$.extraQuestions[0].label").value("T-shirt Size"))
                .andExpect(jsonPath("$.extraQuestions[1].id").value("emergency_contact"))
                .andExpect(jsonPath("$.extraQuestions[1].label").value("Emergency Contact"));
    }

    // -------------------------------------------------------------------------
    // 3. PUT twice (upsert, exactly one row) & updated_at changes
    // -------------------------------------------------------------------------
    @Test
    void putTwiceUpsertExactlyOneRowAndUpdatedAtChanges() throws Exception {
        Event event = createEvent("Upsert Event");

        UpdateRegistrationFormRequest req1 = new UpdateRegistrationFormRequest();
        req1.setMaxResponses(20);
        req1.setOfferedSkills(Collections.emptyList());
        req1.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxResponses").value(20));

        RegistrationForm firstRow = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        Instant firstUpdatedAt = firstRow.getUpdatedAt();
        assertNotNull(firstUpdatedAt);

        Thread.sleep(50); // slight time lapse

        UpdateRegistrationFormRequest req2 = new UpdateRegistrationFormRequest();
        req2.setMaxResponses(40);
        req2.setOfferedSkills(Collections.emptyList());
        req2.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxResponses").value(40));

        // Exactly one row exists
        RegistrationForm secondRow = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals(firstRow.getId(), secondRow.getId());
        assertEquals(40, secondRow.getMaxResponses());
        assertTrue(secondRow.getUpdatedAt().isAfter(firstUpdatedAt), "updated_at must change after PUT");
    }

    // -------------------------------------------------------------------------
    // 4. Validation rules: dates, maxResponses, skills, extraQuestions
    // -------------------------------------------------------------------------
    @Test
    void validationRulesEnforced() throws Exception {
        Event event = createEvent("Validation Rules Event");

        // 4a. closesAt before opensAt -> 400
        UpdateRegistrationFormRequest badDates = new UpdateRegistrationFormRequest();
        badDates.setOpensAt(Instant.parse("2026-12-05T10:00:00Z"));
        badDates.setClosesAt(Instant.parse("2026-12-01T10:00:00Z"));
        badDates.setOfferedSkills(Collections.emptyList());
        badDates.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDates)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("closesAt must be after opensAt"));

        // closesAt equal to opensAt -> 400
        badDates.setClosesAt(badDates.getOpensAt());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDates)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("closesAt must be after opensAt"));

        // 4b. maxResponses bounds (0 and 10001) -> 400
        UpdateRegistrationFormRequest badMax0 = new UpdateRegistrationFormRequest();
        badMax0.setMaxResponses(0);
        badMax0.setOfferedSkills(Collections.emptyList());
        badMax0.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMax0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxResponses must be between 1 and 10000"));

        UpdateRegistrationFormRequest badMax10001 = new UpdateRegistrationFormRequest();
        badMax10001.setMaxResponses(10001);
        badMax10001.setOfferedSkills(Collections.emptyList());
        badMax10001.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMax10001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxResponses must be between 1 and 10000"));

        // 4c. offeredSkills > 50 -> 400
        UpdateRegistrationFormRequest badSkills51 = new UpdateRegistrationFormRequest();
        List<String> fiftyOneSkills = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            fiftyOneSkills.add("Skill " + i);
        }
        badSkills51.setOfferedSkills(fiftyOneSkills);
        badSkills51.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSkills51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offeredSkills must not exceed 50 items"));

        // Skill > 40 chars -> 400
        UpdateRegistrationFormRequest longSkill = new UpdateRegistrationFormRequest();
        longSkill.setOfferedSkills(List.of("S".repeat(41)));
        longSkill.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(longSkill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Each skill must be between 1 and 40 characters"));

        // Skill with control char -> 400
        UpdateRegistrationFormRequest ctrlSkill = new UpdateRegistrationFormRequest();
        ctrlSkill.setOfferedSkills(List.of("Skill\u0000Bad"));
        ctrlSkill.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctrlSkill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offeredSkills contains invalid characters"));

        // Deduplication case-insensitive: "Java" and "java" -> "Java"
        UpdateRegistrationFormRequest dedupeSkill = new UpdateRegistrationFormRequest();
        dedupeSkill.setOfferedSkills(List.of("Java", "java", "JAVA"));
        dedupeSkill.setExtraQuestions(Collections.emptyList());
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dedupeSkill)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeredSkills.length()").value(1))
                .andExpect(jsonPath("$.offeredSkills[0]").value("Java"));

        // 4d. extraQuestions: 3 items -> 400
        UpdateRegistrationFormRequest threeQuestions = new UpdateRegistrationFormRequest();
        threeQuestions.setOfferedSkills(Collections.emptyList());
        threeQuestions.setExtraQuestions(List.of(
                new ExtraQuestionDto("q1", "Question 1"),
                new ExtraQuestionDto("q2", "Question 2"),
                new ExtraQuestionDto("q3", "Question 3")
        ));
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(threeQuestions)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("extraQuestions must not exceed 2 items"));

        // extraQuestions with exactly 2 -> 200
        UpdateRegistrationFormRequest twoQuestions = new UpdateRegistrationFormRequest();
        twoQuestions.setOfferedSkills(Collections.emptyList());
        twoQuestions.setExtraQuestions(List.of(
                new ExtraQuestionDto("q1", "Question 1"),
                new ExtraQuestionDto("q2", "Question 2")
        ));
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(twoQuestions)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraQuestions.length()").value(2));

        // extraQuestions invalid id pattern (uppercase or special chars) -> 400
        UpdateRegistrationFormRequest badQId = new UpdateRegistrationFormRequest();
        badQId.setOfferedSkills(Collections.emptyList());
        badQId.setExtraQuestions(List.of(new ExtraQuestionDto("Question_1!", "Label")));
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badQId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question id must be between 1 and 40 characters of [a-z0-9_-]"));

        // extraQuestions duplicate id -> 400
        UpdateRegistrationFormRequest dupQId = new UpdateRegistrationFormRequest();
        dupQId.setOfferedSkills(Collections.emptyList());
        dupQId.setExtraQuestions(List.of(
                new ExtraQuestionDto("same_id", "Label 1"),
                new ExtraQuestionDto("same_id", "Label 2")
        ));
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupQId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question id must be unique"));

        // extraQuestions label with control char -> 400
        UpdateRegistrationFormRequest ctrlQLabel = new UpdateRegistrationFormRequest();
        ctrlQLabel.setOfferedSkills(Collections.emptyList());
        ctrlQLabel.setExtraQuestions(List.of(new ExtraQuestionDto("q_ctrl", "Label\tTab")));
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctrlQLabel)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("extraQuestions label contains invalid characters"));
    }

    // -------------------------------------------------------------------------
    // 5. maxResponses below responseCount -> 409
    // -------------------------------------------------------------------------
    @Test
    void maxResponsesBelowResponseCountReturns409() throws Exception {
        Event event = createEvent("Response Count Check Event");

        // Seed 3 volunteers for this event
        for (int i = 1; i <= 3; i++) {
            Volunteer v = new Volunteer();
            v.setEventId(event.getId());
            v.setFullName("Vol " + i);
            v.setEmail("vol" + i + "@college.edu");
            v.setRegisteredAt(Instant.now());
            volunteerRepository.save(v);
        }
        assertEquals(3, volunteerRepository.countByEventId(event.getId()));

        UpdateRegistrationFormRequest req = new UpdateRegistrationFormRequest();
        req.setMaxResponses(2); // lower than 3
        req.setOfferedSkills(Collections.emptyList());
        req.setExtraQuestions(Collections.emptyList());

        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("maxResponses cannot be lower than current response count"));
    }

    // -------------------------------------------------------------------------
    // 6. Unknown field (including status) -> 400
    // -------------------------------------------------------------------------
    @Test
    void unknownFieldReturns400() throws Exception {
        Event event = createEvent("Unknown Field Event");

        Map<String, Object> badMap = Map.of(
                "maxResponses", 100,
                "offeredSkills", List.of(),
                "extraQuestions", List.of(),
                "status", "open" // status not editable via PUT
        );

        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));
    }

    // -------------------------------------------------------------------------
    // 7. Transitions: draft->open, open->close, closed->open OK; invalid -> 409
    //    open on an event with no row creates an open row; updated_at changes
    // -------------------------------------------------------------------------
    @Test
    void statusTransitionsAndOpenOnEmptyCreatesRow() throws Exception {
        Event event = createEvent("Transitions Event");

        // 7a. open on an event with no row creates an open row
        mockMvc.perform(post("/api/events/" + event.getId() + "/registration-form/open")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"));

        RegistrationForm row1 = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals("open", row1.getStatus());
        Instant updateTime1 = row1.getUpdatedAt();
        assertNotNull(updateTime1);

        // open -> open -> 409
        mockMvc.perform(post("/api/events/" + event.getId() + "/registration-form/open")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Registration form is already open"));

        Thread.sleep(50);

        // 7b. open -> close -> 200
        mockMvc.perform(post("/api/events/" + event.getId() + "/registration-form/close")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));

        RegistrationForm row2 = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals("closed", row2.getStatus());
        assertTrue(row2.getUpdatedAt().isAfter(updateTime1), "updated_at must change on close");

        // closed -> close -> 409
        mockMvc.perform(post("/api/events/" + event.getId() + "/registration-form/close")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only open registration forms can be closed"));

        Thread.sleep(50);

        // 7c. closed -> open -> 200
        mockMvc.perform(post("/api/events/" + event.getId() + "/registration-form/open")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"));

        RegistrationForm row3 = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals("open", row3.getStatus());
        assertTrue(row3.getUpdatedAt().isAfter(row2.getUpdatedAt()), "updated_at must change on reopen");

        // 7d. draft -> close -> 409
        Event draftEvent = createEvent("Draft Transition Event");
        mockMvc.perform(post("/api/events/" + draftEvent.getId() + "/registration-form/close")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Only open registration forms can be closed"));
    }

    // -------------------------------------------------------------------------
    // 8. Security & Permissions (404, 403, 401, CSRF)
    // -------------------------------------------------------------------------
    @Test
    void securityAndPermissionsValidation() throws Exception {
        UUID unknownEventId = UUID.randomUUID();

        // 404 on unknown event
        mockMvc.perform(get("/api/events/" + unknownEventId + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found: " + unknownEventId));

        mockMvc.perform(put("/api/events/" + unknownEventId + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found: " + unknownEventId));

        mockMvc.perform(post("/api/events/" + unknownEventId + "/registration-form/open")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/events/" + unknownEventId + "/registration-form/close")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound());

        Event event = createEvent("Security Test Event");
        String baseUri = "/api/events/" + event.getId() + "/registration-form";

        // Volunteer gets 403 on all 4 endpoints
        mockMvc.perform(get(baseUri)
                        .cookie(new Cookie("eventra_token", volunteerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(put(baseUri)
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(baseUri + "/open")
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(baseUri + "/close")
                        .cookie(new Cookie("eventra_token", volunteerToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());

        // No token gets 401
        mockMvc.perform(get(baseUri))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        // Missing CSRF header gets 403 on writes
        mockMvc.perform(put(baseUri)
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));

        mockMvc.perform(post(baseUri + "/open")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));

        mockMvc.perform(post(baseUri + "/close")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));
    }

    // -------------------------------------------------------------------------
    // 9. Run 5a-fix specific tests
    // -------------------------------------------------------------------------
    @Test
    void run5aFixTests() throws Exception {
        Event event = createEvent("Run 5a Fix Event");

        // 9a. PUT missing offeredSkills -> 400
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extraQuestions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("offeredSkills is required"));

        // 9b. PUT missing extraQuestions -> 400
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("extraQuestions is required"));

        // 9c. maxResponses equal to responseCount -> 200
        // Seed 2 volunteers
        for (int i = 1; i <= 2; i++) {
            Volunteer v = new Volunteer();
            v.setEventId(event.getId());
            v.setFullName("Fix Vol " + i);
            v.setEmail("fixvol" + i + "@college.edu");
            v.setRegisteredAt(Instant.now());
            volunteerRepository.save(v);
        }
        assertEquals(2, volunteerRepository.countByEventId(event.getId()));

        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxResponses\":2,\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxResponses").value(2));

        // 9d. maxResponses 1 and 10000 -> 200
        Event eventBounds = createEvent("Bounds Event");
        mockMvc.perform(put("/api/events/" + eventBounds.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxResponses\":1,\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxResponses").value(1));

        mockMvc.perform(put("/api/events/" + eventBounds.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxResponses\":10000,\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxResponses").value(10000));

        // 9e. skill "  Java  " stored as "Java"
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[\"  Java  \"],\"extraQuestions\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeredSkills[0]").value("Java"));

        // Verify in DB
        RegistrationForm formSkill = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals(List.of("Java"), formSkill.getOfferedSkills());

        // 9f. blank skill "   " -> 400
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[\"   \"],\"extraQuestions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Each skill must be between 1 and 40 characters"));

        // 9g. label "  Size  " stored as "Size"
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[],\"extraQuestions\":[{\"id\":\"tshirt\",\"label\":\"  Size  \"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraQuestions[0].label").value("Size"));

        RegistrationForm formLabel = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals("Size", formLabel.getExtraQuestions().get(0).getLabel());

        // 9h. label of 201 chars -> 400
        String longLabel = "L".repeat(201);
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[],\"extraQuestions\":[{\"id\":\"tshirt\",\"label\":\"" + longLabel + "\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question label must be between 1 and 200 characters"));

        // 9i. question id of 41 chars -> 400
        String longId = "q".repeat(41);
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkills\":[],\"extraQuestions\":[{\"id\":\"" + longId + "\",\"label\":\"Label\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question id must be between 1 and 40 characters of [a-z0-9_-]"));

        // 9j. "opensAt":"not-a-date" -> 400 (not 500)
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opensAt\":\"not-a-date\",\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON request body"));

        // 9k. "maxResponses":"abc" -> 400
        mockMvc.perform(put("/api/events/" + event.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxResponses\":\"abc\",\"offeredSkills\":[],\"extraQuestions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON request body"));

        // 9l. GET after seeding 3 volunteers returns responseCount exactly 3
        Event volCountEvent = createEvent("Vol Count Event");
        for (int i = 1; i <= 3; i++) {
            Volunteer v = new Volunteer();
            v.setEventId(volCountEvent.getId());
            v.setFullName("Count Vol " + i);
            v.setEmail("countvol" + i + "@college.edu");
            v.setRegisteredAt(Instant.now());
            volunteerRepository.save(v);
        }
        mockMvc.perform(get("/api/events/" + volCountEvent.getId() + "/registration-form")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseCount").value(3));
    }
}
