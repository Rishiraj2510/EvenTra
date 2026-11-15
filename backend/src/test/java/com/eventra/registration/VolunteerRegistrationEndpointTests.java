package com.eventra.registration;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.event.EventService;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ExtraQuestion;
import com.eventra.model.SkillLevel;
import com.eventra.registration.dto.VolunteerRegistrationRequest;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class VolunteerRegistrationEndpointTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventService eventService;

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
    private User volunteer1;
    private User volunteer2;
    private String organiserToken;
    private String volunteer1Token;
    private String volunteer2Token;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        organiser = createUser("organiser");
        volunteer1 = createUser("volunteer");
        volunteer2 = createUser("volunteer");
        organiserToken = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());
        volunteer1Token = jwtService.generateToken(volunteer1.getId(), volunteer1.getRole(), volunteer1.getTokenVersion());
        volunteer2Token = jwtService.generateToken(volunteer2.getId(), volunteer2.getRole(), volunteer2.getTokenVersion());
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
        Map<String, Object> cert = new HashMap<>();
        cert.put("type", "standard");
        event.setCertificateTemplate(cert);
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    private RegistrationForm createForm(UUID eventId, String status, Instant opensAt, Instant closesAt,
                                        Integer maxResponses, List<String> skills, List<ExtraQuestion> questions) {
        RegistrationForm form = new RegistrationForm();
        form.setEventId(eventId);
        form.setStatus(status);
        form.setOpensAt(opensAt);
        form.setClosesAt(closesAt);
        form.setMaxResponses(maxResponses);
        form.setOfferedSkills(skills != null ? skills : Collections.emptyList());
        form.setExtraQuestions(questions != null ? questions : Collections.emptyList());
        return registrationFormRepository.save(form);
    }

    private VolunteerRegistrationRequest validRequest() {
        VolunteerRegistrationRequest req = new VolunteerRegistrationRequest();
        req.setFullName("John Doe");
        req.setPhone("+1234567890");
        req.setDepartment("Computer Science");
        req.setYearOfStudy(2);
        req.setPastEventsCount(3);
        req.setMaxHoursPerEvent(8);
        req.setNotificationPreference("email");
        req.setInterests(List.of("Tech", "Design"));
        req.setAvailability(List.of(new AvailabilitySlot("Monday", "Morning")));
        req.setSkills(List.of(new SkillLevel("Audio Board", "Beginner")));
        req.setExtraAnswers(Map.of("tshirt", "Large"));
        return req;
    }

    // -------------------------------------------------------------------------
    // 1. GET /api/volunteer/events list & state computation
    // -------------------------------------------------------------------------
    @Test
    void listVolunteerEventsExcludesDraftAndComputesState() throws Exception {
        // Draft event -> excluded
        createEvent("Draft Event", "draft");

        // Event with no form -> state "closed"
        Event noFormEvent = createEvent("No Form Event", "planning");

        // Event done -> state "closed"
        Event doneEvent = createEvent("Done Event", "done");
        createForm(doneEvent.getId(), "open", null, null, null, null, null);

        // Form draft -> state "closed"
        Event draftFormEvent = createEvent("Draft Form Event", "planning");
        createForm(draftFormEvent.getId(), "draft", null, null, null, null, null);

        // Form before opensAt -> state "closed"
        Event futureOpensEvent = createEvent("Future Opens Event", "planning");
        createForm(futureOpensEvent.getId(), "open", Instant.now().plusSeconds(3600), null, null, null, null);

        // Form after closesAt -> state "closed"
        Event pastClosesEvent = createEvent("Past Closes Event", "planning");
        createForm(pastClosesEvent.getId(), "open", null, Instant.now().minusSeconds(3600), null, null, null);

        // Form open & available -> state "open"
        Event openEvent = createEvent("Open Event", "planning");
        openEvent.setStartDate(Instant.now().plusSeconds(7200)); // starts later
        eventRepository.save(openEvent);
        createForm(openEvent.getId(), "open", null, null, 10, null, null);

        // Form open & full -> state "full"
        Event fullEvent = createEvent("Full Event", "planning");
        fullEvent.setStartDate(Instant.now().plusSeconds(1800)); // starts earlier
        eventRepository.save(fullEvent);
        createForm(fullEvent.getId(), "open", null, null, 1, null, null);
        Volunteer seededVol = new Volunteer();
        seededVol.setEventId(fullEvent.getId());
        seededVol.setFullName("Seeded");
        seededVol.setEmail("seed@college.edu");
        seededVol.setRegisteredAt(Instant.now());
        volunteerRepository.save(seededVol);

        // Event where caller is registered -> state "registered"
        Event regEvent = createEvent("Registered Event", "planning");
        regEvent.setStartDate(Instant.now().plusSeconds(3600)); // middle start
        eventRepository.save(regEvent);
        createForm(regEvent.getId(), "open", null, null, 10, null, null);
        Volunteer myVol = new Volunteer();
        myVol.setEventId(regEvent.getId());
        myVol.setUserId(volunteer1.getId());
        myVol.setFullName(volunteer1.getFullName());
        myVol.setEmail(volunteer1.getEmail());
        myVol.setRegisteredAt(Instant.now());
        volunteerRepository.save(myVol);

        MvcResult result = mockMvc.perform(get("/api/volunteer/events")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // certificateTemplate, counts, maxResponses must never appear in response
        assertFalse(body.contains("certificateTemplate"));
        assertFalse(body.contains("maxResponses"));
        assertFalse(body.contains("responseCount"));
        assertFalse(body.contains("Draft Event"));

        // Parse list and verify states
        List<?> list = objectMapper.readValue(body, List.class);
        assertEquals(8, list.size()); // all non-draft events

        // Verify ordering: startDate ascending, then id
        Instant prevDate = Instant.MIN;
        for (Object itemObj : list) {
            Map<?, ?> item = (Map<?, ?>) itemObj;
            String startStr = (String) item.get("startDate");
            Instant curDate = startStr != null ? Instant.parse(startStr) : Instant.MIN;
            assertTrue(!curDate.isBefore(prevDate), "Events list must be ordered by startDate ascending");
            prevDate = curDate;
        }

        Map<String, String> states = new HashMap<>();
        for (Object itemObj : list) {
            Map<?, ?> item = (Map<?, ?>) itemObj;
            states.put((String) item.get("name"), (String) item.get("state"));
        }

        assertEquals("closed", states.get("No Form Event"));
        assertEquals("closed", states.get("Done Event"));
        assertEquals("closed", states.get("Draft Form Event"));
        assertEquals("closed", states.get("Future Opens Event"));
        assertEquals("closed", states.get("Past Closes Event"));
        assertEquals("open", states.get("Open Event"));
        assertEquals("full", states.get("Full Event"));
        assertEquals("registered", states.get("Registered Event"));
    }

    // -------------------------------------------------------------------------
    // 2. GET /api/events/{eventId}/registration-form/view
    // -------------------------------------------------------------------------
    @Test
    void getRegistrationFormView() throws Exception {
        Event event = createEvent("View Event", "planning");
        createForm(event.getId(), "open", null, null, 20,
                List.of("Sound Tech", "Lighting"),
                List.of(new ExtraQuestion("size", "Shirt size")));

        mockMvc.perform(get("/api/events/" + event.getId() + "/registration-form/view")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.state").value("open"))
                .andExpect(jsonPath("$.offeredSkills[0]").value("Sound Tech"))
                .andExpect(jsonPath("$.offeredSkills[1]").value("Lighting"))
                .andExpect(jsonPath("$.extraQuestions[0].id").value("size"))
                .andExpect(jsonPath("$.extraQuestions[0].label").value("Shirt size"));

        // Draft event -> 404
        Event draftEvent = createEvent("Draft View Event", "draft");
        createForm(draftEvent.getId(), "open", null, null, null, null, null);
        mockMvc.perform(get("/api/events/" + draftEvent.getId() + "/registration-form/view")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // 3. POST /api/events/{eventId}/registrations/me -> 201 and validations
    // -------------------------------------------------------------------------
    @Test
    void registerVolunteerSuccessAndStoredValues() throws Exception {
        Event event = createEvent("Register Event", "planning");
        createForm(event.getId(), "open", null, null, 50,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        VolunteerRegistrationRequest req = validRequest();
        // Skill name in lowercase -> stored with canonical casing "Audio Board"
        req.setSkills(List.of(new SkillLevel("audio board", "Beginner")));

        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.userId").value(volunteer1.getId().toString()))
                .andExpect(jsonPath("$.email").value(volunteer1.getEmail()))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.skills[0].name").value("Audio Board"))
                .andExpect(jsonPath("$.skills[0].level").value("Beginner"))
                .andExpect(jsonPath("$.extraAnswers").doesNotExist()); // never exposed

        // Verify in DB directly
        Volunteer stored = volunteerRepository.findByEventIdAndUserId(event.getId(), volunteer1.getId()).orElseThrow();
        assertEquals("Audio Board", stored.getSkills().get(0).getName());
        assertEquals("Large", stored.getExtraAnswers().get("tshirt"));
        assertEquals(volunteer1.getEmail(), stored.getEmail());
    }

    @Test
    void registerValidationAndConflictErrors() throws Exception {
        Event event = createEvent("Validation Reg Event", "planning");
        createForm(event.getId(), "open", null, null, 50,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        // a. Disallowed fields in body (unknown fields -> 400)
        Map<String, Object> badMap = new HashMap<>(objectMapper.convertValue(validRequest(), Map.class));
        badMap.put("email", "hacker@test.com");
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        badMap.remove("email");
        badMap.put("userId", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        badMap.remove("userId");
        badMap.put("eventId", event.getId().toString());
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        badMap.remove("eventId");
        badMap.put("status", "inactive");
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badMap)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown field"));

        // b. Unoffered skill -> 400
        VolunteerRegistrationRequest unofferedSkillReq = validRequest();
        unofferedSkillReq.setSkills(List.of(new SkillLevel("Rocket Science", "Beginner")));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unofferedSkillReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Skill is not offered on this registration form"));

        // c. Unknown extraAnswers key -> 400
        VolunteerRegistrationRequest unknownKeyReq = validRequest();
        unknownKeyReq.setExtraAnswers(Map.of("non_existent_key", "value"));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownKeyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown extra question key"));

        // d. Field boundaries: yearOfStudy 0 and 5 -> 400 (yearOfStudy must be between 1 and 4)
        VolunteerRegistrationRequest badYear0 = validRequest();
        badYear0.setYearOfStudy(0);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badYear0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("yearOfStudy must be between 1 and 4"));

        VolunteerRegistrationRequest badYear5 = validRequest();
        badYear5.setYearOfStudy(5);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badYear5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("yearOfStudy must be between 1 and 4"));

        // yearOfStudy 1 and 4 accepted
        Event eventYears = createEvent("Years Event", "planning");
        createForm(eventYears.getId(), "open", null, null, 50,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        VolunteerRegistrationRequest okYear1 = validRequest();
        okYear1.setYearOfStudy(1);
        mockMvc.perform(post("/api/events/" + eventYears.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okYear1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearOfStudy").value(1));

        VolunteerRegistrationRequest okYear4 = validRequest();
        okYear4.setYearOfStudy(4);
        mockMvc.perform(post("/api/events/" + eventYears.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okYear4)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yearOfStudy").value(4));

        // maxHoursPerEvent 0 and 25 -> 400
        VolunteerRegistrationRequest badHours0 = validRequest();
        badHours0.setMaxHoursPerEvent(0);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badHours0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxHoursPerEvent must be between 1 and 24"));

        VolunteerRegistrationRequest badHours25 = validRequest();
        badHours25.setMaxHoursPerEvent(25);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badHours25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxHoursPerEvent must be between 1 and 24"));

        // pastEventsCount -1 and 101 -> 400
        VolunteerRegistrationRequest badPastNeg = validRequest();
        badPastNeg.setPastEventsCount(-1);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPastNeg)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("pastEventsCount must be between 0 and 100"));

        VolunteerRegistrationRequest badPast101 = validRequest();
        badPast101.setPastEventsCount(101);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPast101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("pastEventsCount must be between 0 and 100"));

        // skills 31 -> 400
        VolunteerRegistrationRequest badSkills31 = validRequest();
        List<SkillLevel> list31 = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            list31.add(new SkillLevel("Audio Board", "Beginner"));
        }
        badSkills31.setSkills(list31);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSkills31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("skills must not exceed 30 items"));

        // availability 22 -> 400
        VolunteerRegistrationRequest badAvail22 = validRequest();
        List<AvailabilitySlot> list22 = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            list22.add(new AvailabilitySlot("Monday", "Morning"));
        }
        badAvail22.setAvailability(list22);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badAvail22)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("availability must not exceed 21 entries"));

        // availability duplicate pair -> 400
        VolunteerRegistrationRequest dupAvail = validRequest();
        dupAvail.setAvailability(List.of(
                new AvailabilitySlot("Monday", "Morning"),
                new AvailabilitySlot("Monday", "Morning")
        ));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupAvail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Duplicate availability pair: Monday Morning"));

        // interests 21 -> 400
        VolunteerRegistrationRequest badInterests21 = validRequest();
        List<String> listInt21 = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            listInt21.add("Interest " + i);
        }
        badInterests21.setInterests(listInt21);
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badInterests21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("interests must not exceed 20 items"));

        // bad notificationPreference -> 400
        VolunteerRegistrationRequest badNotif = validRequest();
        badNotif.setNotificationPreference("sms");
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badNotif)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("notificationPreference must be email or whatsapp"));

        // missing required field -> 400
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // malformed JSON value -> 400
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearOfStudy\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON request body"));
    }

    @Test
    void registerWhenClosedOrDuplicateReturns409() throws Exception {
        Event event = createEvent("Closed Reg Event", "planning");
        createForm(event.getId(), "closed", null, null, 10, List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        // Form closed -> 409
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Registration is closed"));

        // Now open it
        RegistrationForm form = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        form.setStatus("open");
        registrationFormRepository.save(form);

        // Register once -> 201
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        // Register second time -> 409 "Already registered"
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Already registered"));
    }

    // -------------------------------------------------------------------------
    // 4. PUT, GET, DELETE /api/events/{eventId}/registrations/me
    // -------------------------------------------------------------------------
    @Test
    void getUpdateAndDeleteMyRegistration() throws Exception {
        Event event = createEvent("CRUD Reg Event", "planning");
        createForm(event.getId(), "open", null, null, 50,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        // GET before register -> 404
        mockMvc.perform(get("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isNotFound());

        // Register
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        // Volunteer 2 GET -> 404
        mockMvc.perform(get("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token)))
                .andExpect(status().isNotFound());

        // Volunteer 1 GET -> 200
        mockMvc.perform(get("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.department").value("Computer Science"));

        // PUT full replace
        VolunteerRegistrationRequest updateReq = validRequest();
        updateReq.setFullName("Jane Smith");
        updateReq.setDepartment("Electrical Eng");
        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Jane Smith"))
                .andExpect(jsonPath("$.department").value("Electrical Eng"));

        // PUT when form closed -> 409
        RegistrationForm form = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        form.setStatus("closed");
        registrationFormRepository.save(form);

        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Registration is closed"));

        // Reopen form
        form.setStatus("open");
        registrationFormRepository.save(form);

        // Volunteer 2 DELETE -> 404
        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound());

        // Volunteer 2 PUT on closed form -> 404 (caller has no registration)
        form.setStatus("closed");
        registrationFormRepository.save(form);
        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isNotFound());

        // Volunteer 2 DELETE on closed form -> 404 (caller has no registration)
        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound());

        // Volunteer 1 (has registration) on closed form -> 409 for PUT and DELETE
        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Registration is closed"));

        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Registration is closed"));

        // Reopen form for Volunteer 1 DELETE
        form.setStatus("open");
        registrationFormRepository.save(form);

        // Volunteer 1 DELETE -> 204
        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNoContent());

        // GET me after delete -> 404
        mockMvc.perform(get("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isNotFound());

        // Can register again -> 201
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // 5. Security & CSRF
    // -------------------------------------------------------------------------
    @Test
    void securityAndCsrfEnforced() throws Exception {
        Event event = createEvent("Sec Event", "planning");
        createForm(event.getId(), "open", null, null, 10, List.of("Audio Board"), null);

        // Organiser gets 403 on all six endpoints
        mockMvc.perform(get("/api/volunteer/events")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/events/" + event.getId() + "/registration-form/view")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());

        // No token -> 401
        mockMvc.perform(get("/api/volunteer/events"))
                .andExpect(status().isUnauthorized());

        // Missing CSRF -> 403 on POST, PUT, DELETE
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));

        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));

        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));
    }

    // -------------------------------------------------------------------------
    // 6. Concurrency Tests (Real Docker PostgreSQL, ExecutorService + CountDownLatch)
    // -------------------------------------------------------------------------
    @Test
    void concurrencyMaxResponsesOneFiveVolunteers() throws Exception {
        Event event = createEvent("Concurrent Cap Event", "planning");
        createForm(event.getId(), "open", null, null, 1, List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        int threads = 5;
        List<User> users = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            User u = createUser("volunteer");
            users.add(u);
            tokens.add(jwtService.generateToken(u.getId(), u.getRole(), u.getTokenVersion()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger count201 = new AtomicInteger();
        AtomicInteger count409 = new AtomicInteger();
        AtomicInteger otherErrors = new AtomicInteger();
        List<String> bodies409 = Collections.synchronizedList(new ArrayList<>());

        VolunteerRegistrationRequest req = validRequest();
        String jsonBody = objectMapper.writeValueAsString(req);

        for (int i = 0; i < threads; i++) {
            final String token = tokens.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                                    .cookie(new Cookie("eventra_token", token))
                                    .header(CSRF_HEADER, CSRF_VALUE)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonBody))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    if (status == 201) {
                        count201.incrementAndGet();
                    } else if (status == 409) {
                        count409.incrementAndGet();
                        bodies409.add(res.getResponse().getContentAsString());
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, count201.get(), "Exactly one volunteer must get 201");
        assertEquals(4, count409.get(), "Four volunteers must get 409");
        assertEquals(0, otherErrors.get(), "No 500 or other errors allowed");
        for (String b : bodies409) {
            assertEquals("{\"error\":\"Registration is full\"}", b);
        }
        assertEquals(1, volunteerRepository.countByEventId(event.getId()), "Volunteers count for event must be exactly 1");
    }

    @Test
    void concurrencySameVolunteerSimultaneousPosts() throws Exception {
        Event event = createEvent("Concurrent Duplicate Event", "planning");
        createForm(event.getId(), "open", null, null, 10, List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger count201 = new AtomicInteger();
        AtomicInteger count409 = new AtomicInteger();
        AtomicInteger otherErrors = new AtomicInteger();
        List<String> bodies409 = Collections.synchronizedList(new ArrayList<>());

        String jsonBody = objectMapper.writeValueAsString(validRequest());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                                    .cookie(new Cookie("eventra_token", volunteer1Token))
                                    .header(CSRF_HEADER, CSRF_VALUE)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonBody))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    if (status == 201) {
                        count201.incrementAndGet();
                    } else if (status == 409) {
                        count409.incrementAndGet();
                        bodies409.add(res.getResponse().getContentAsString());
                    } else {
                        otherErrors.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(1, count201.get(), "Exactly one 201 for the same user");
        assertEquals(1, count409.get(), "Exactly one 409 for duplicate");
        assertEquals(0, otherErrors.get(), "No 500 or other status");
        assertEquals(1, bodies409.size());
        assertEquals("{\"error\":\"Already registered\"}", bodies409.get(0));
        assertEquals(1, volunteerRepository.countByEventId(event.getId()), "Exactly one row stored");
    }

    @Test
    void concurrencyDeleteEventWhileVolunteerRegisters() throws Exception {
        int iterations = 10;
        List<UUID> outcomeBEvents = new ArrayList<>();

        for (int iter = 0; iter < iterations; iter++) {
            Event event = createEvent("Concurrent Delete Event " + iter, "planning");
            createForm(event.getId(), "open", null, null, 10, List.of("Audio Board"),
                    List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

            User vol = createUser("volunteer");
            String volToken = jwtService.generateToken(vol.getId(), vol.getRole(), vol.getTokenVersion());

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            AtomicInteger deleteStatus = new AtomicInteger();
            AtomicInteger registerStatus = new AtomicInteger();

            String jsonBody = objectMapper.writeValueAsString(validRequest());

            // Thread 1: Organiser DELETE event
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(delete("/api/events/" + event.getId())
                                    .cookie(new Cookie("eventra_token", organiserToken))
                                    .header(CSRF_HEADER, CSRF_VALUE))
                            .andReturn();
                    deleteStatus.set(res.getResponse().getStatus());
                } catch (Exception e) {
                    deleteStatus.set(-1);
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread 2: Volunteer POST registration
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                                    .cookie(new Cookie("eventra_token", volToken))
                                    .header(CSRF_HEADER, CSRF_VALUE)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonBody))
                            .andReturn();
                    registerStatus.set(res.getResponse().getStatus());
                } catch (Exception e) {
                    registerStatus.set(-1);
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            int del = deleteStatus.get();
            int reg = registerStatus.get();

            // Outcome must be either A (delete 204 + register 404) or B (delete 409 + register 201)
            boolean outcomeA = (del == 204 && reg == 404);
            boolean outcomeB = (del == 409 && reg == 201);

            assertTrue(outcomeA || outcomeB,
                    "Iteration " + iter + " outcome must be either (204, 404) or (409, 201), was del=" + del + ", reg=" + reg);

            if (outcomeA) {
                assertFalse(eventRepository.existsById(event.getId()), "Event must not exist in outcome A");
                assertEquals(0, volunteerRepository.countByEventId(event.getId()), "No orphan volunteers in outcome A");
            } else {
                assertTrue(eventRepository.existsById(event.getId()), "Event must exist in outcome B");
                assertEquals(1, volunteerRepository.countByEventId(event.getId()), "Exactly 1 volunteer in outcome B");
                outcomeBEvents.add(event.getId());
            }
        }

        // Clean up events from outcome B
        for (UUID eventId : outcomeBEvents) {
            try {
                // Remove volunteers first or let cascade delete
                eventRepository.deleteById(eventId);
            } catch (Exception ignored) {
            }
        }
    }

    // -------------------------------------------------------------------------
    // 7. Additional Tests: Nested nulls, Failed validation row checks, Isolation
    // -------------------------------------------------------------------------
    @Test
    void nestedNullItemsReturn400() throws Exception {
        Event event = createEvent("Nested Null Event", "planning");
        createForm(event.getId(), "open", null, null, 10,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        // Null skill name -> 400 (not 500)
        VolunteerRegistrationRequest nullSkillName = validRequest();
        nullSkillName.setSkills(Collections.singletonList(new SkillLevel(null, "Beginner")));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullSkillName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Skill name and level are required"));

        // Null skill level -> 400 (not 500)
        VolunteerRegistrationRequest nullSkillLevel = validRequest();
        nullSkillLevel.setSkills(Collections.singletonList(new SkillLevel("Audio Board", null)));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullSkillLevel)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Skill name and level are required"));

        // Null availability day -> 400 (not 500)
        VolunteerRegistrationRequest nullAvailDay = validRequest();
        nullAvailDay.setAvailability(Collections.singletonList(new AvailabilitySlot(null, "Morning")));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullAvailDay)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Availability day and slot are required"));

        // Null availability slot -> 400 (not 500)
        VolunteerRegistrationRequest nullAvailSlot = validRequest();
        nullAvailSlot.setAvailability(Collections.singletonList(new AvailabilitySlot("Monday", null)));
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullAvailSlot)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Availability day and slot are required"));
    }

    @Test
    void failedValidationLeavesNoVolunteersRow() throws Exception {
        Event event = createEvent("Failed Validation Row Event", "planning");
        createForm(event.getId(), "open", null, null, 10,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        VolunteerRegistrationRequest unoffered = validRequest();
        unoffered.setSkills(List.of(new SkillLevel("Unoffered Skill", "Beginner")));

        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unoffered)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Skill is not offered on this registration form"));

        assertEquals(0, volunteerRepository.countByEventId(event.getId()), "Failed validation must leave no rows");
    }

    @Test
    void volunteerBCannotReadUpdateOrDeleteVolunteerARegistration() throws Exception {
        Event event = createEvent("Isolation Event", "planning");
        createForm(event.getId(), "open", null, null, 10,
                List.of("Audio Board"),
                List.of(new ExtraQuestion("tshirt", "T-shirt Size")));

        // Volunteer A (volunteer1) registers
        mockMvc.perform(post("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer1Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        // Volunteer B (volunteer2) GET -> 404
        mockMvc.perform(get("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Registration not found"));

        // Volunteer B PUT -> 404
        VolunteerRegistrationRequest bUpdate = validRequest();
        bUpdate.setFullName("Hacker Name");
        mockMvc.perform(put("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bUpdate)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Registration not found"));

        // Volunteer B DELETE -> 404
        mockMvc.perform(delete("/api/events/" + event.getId() + "/registrations/me")
                        .cookie(new Cookie("eventra_token", volunteer2Token))
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Registration not found"));

        // Volunteer A's registration is completely unchanged
        Volunteer volA = volunteerRepository.findByEventIdAndUserId(event.getId(), volunteer1.getId()).orElseThrow();
        assertEquals("John Doe", volA.getFullName());
    }
}
