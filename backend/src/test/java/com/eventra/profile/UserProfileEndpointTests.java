package com.eventra.profile;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.SkillLevel;
import com.eventra.profile.dto.UpdateUserProfileRequest;
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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
class UserProfileEndpointTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

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

    private UpdateUserProfileRequest validProfileRequest() {
        UpdateUserProfileRequest req = new UpdateUserProfileRequest();
        req.setFullName("Updated Full Name");
        req.setPhone("+1234567890");
        req.setDepartment("Mechanical Engineering");
        req.setYearOfStudy(2);
        req.setMaxHoursPerEvent(12);
        req.setNotificationPreference("email");
        req.setInterests(List.of("Robotics", "Design"));
        req.setAvailability(List.of(new AvailabilitySlot("Monday", "Morning")));
        req.setSkills(List.of(new SkillLevel("3D Printing", "beginner")));
        return req;
    }

    @Test
    void getProfileFreshUserShape() throws Exception {
        mockMvc.perform(get("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(volunteerA.getEmail()))
                .andExpect(jsonPath("$.fullName").value(volunteerA.getFullName()))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.yearOfStudy").doesNotExist())
                .andExpect(jsonPath("$.skills").isArray())
                .andExpect(jsonPath("$.skills.length()").value(0))
                .andExpect(jsonPath("$.availability").isArray())
                .andExpect(jsonPath("$.availability.length()").value(0))
                .andExpect(jsonPath("$.interests").isArray())
                .andExpect(jsonPath("$.interests.length()").value(0))
                .andExpect(jsonPath("$.pastEventsCount").value(0));
    }

    @Test
    void getProfileAfterPutReflectsChanges() throws Exception {
        UpdateUserProfileRequest req = validProfileRequest();
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Full Name"))
                .andExpect(jsonPath("$.phone").value("+1234567890"))
                .andExpect(jsonPath("$.department").value("Mechanical Engineering"))
                .andExpect(jsonPath("$.yearOfStudy").value(2))
                .andExpect(jsonPath("$.maxHoursPerEvent").value(12))
                .andExpect(jsonPath("$.notificationPreference").value("email"))
                .andExpect(jsonPath("$.skills[0].name").value("3D Printing"))
                .andExpect(jsonPath("$.skills[0].level").value("Beginner"));
    }

    @Test
    void putReplacesAllFields() throws Exception {
        UpdateUserProfileRequest req = validProfileRequest();
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Full Name"))
                .andExpect(jsonPath("$.phone").value("+1234567890"))
                .andExpect(jsonPath("$.department").value("Mechanical Engineering"))
                .andExpect(jsonPath("$.yearOfStudy").value(2))
                .andExpect(jsonPath("$.maxHoursPerEvent").value(12))
                .andExpect(jsonPath("$.notificationPreference").value("email"))
                .andExpect(jsonPath("$.skills[0].name").value("3D Printing"))
                .andExpect(jsonPath("$.skills[0].level").value("Beginner"));
    }

    @Test
    void putMissingEachRequiredFieldReturns400() throws Exception {
        List<String> requiredFields = List.of(
                "fullName", "phone", "department", "yearOfStudy", "skills",
                "availability", "maxHoursPerEvent", "interests", "notificationPreference"
        );

        for (String field : requiredFields) {
            Map<String, Object> map = new HashMap<>(objectMapper.convertValue(validProfileRequest(), Map.class));
            map.remove(field);
            mockMvc.perform(put("/api/me/profile")
                            .cookie(new Cookie("eventra_token", volunteerAToken))
                            .header(CSRF_HEADER, CSRF_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(map)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(field + " is required"));
        }
    }

    @Test
    void putYearOfStudyBoundaries() throws Exception {
        // 0 -> 400
        UpdateUserProfileRequest badYear0 = validProfileRequest();
        badYear0.setYearOfStudy(0);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badYear0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("yearOfStudy must be between 1 and 4"));

        // 5 -> 400
        UpdateUserProfileRequest badYear5 = validProfileRequest();
        badYear5.setYearOfStudy(5);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badYear5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("yearOfStudy must be between 1 and 4"));

        // 1 -> 200
        UpdateUserProfileRequest okYear1 = validProfileRequest();
        okYear1.setYearOfStudy(1);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okYear1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearOfStudy").value(1));

        // 4 -> 200
        UpdateUserProfileRequest okYear4 = validProfileRequest();
        okYear4.setYearOfStudy(4);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okYear4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearOfStudy").value(4));
    }

    @Test
    void putMaxHoursBoundaries() throws Exception {
        // 0 -> 400
        UpdateUserProfileRequest badHours0 = validProfileRequest();
        badHours0.setMaxHoursPerEvent(0);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badHours0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxHoursPerEvent must be between 1 and 24"));

        // 25 -> 400
        UpdateUserProfileRequest badHours25 = validProfileRequest();
        badHours25.setMaxHoursPerEvent(25);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badHours25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("maxHoursPerEvent must be between 1 and 24"));

        // 1 -> 200
        UpdateUserProfileRequest okHours1 = validProfileRequest();
        okHours1.setMaxHoursPerEvent(1);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okHours1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxHoursPerEvent").value(1));

        // 24 -> 200
        UpdateUserProfileRequest okHours24 = validProfileRequest();
        okHours24.setMaxHoursPerEvent(24);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okHours24)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxHoursPerEvent").value(24));
    }

    @Test
    void putSkillsLimit30() throws Exception {
        UpdateUserProfileRequest badSkills31 = validProfileRequest();
        List<SkillLevel> skills31 = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            skills31.add(new SkillLevel("Skill" + i, "Beginner"));
        }
        badSkills31.setSkills(skills31);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSkills31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("skills must not exceed 30 items"));

        UpdateUserProfileRequest okSkills30 = validProfileRequest();
        List<SkillLevel> skills30 = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            skills30.add(new SkillLevel("Skill" + i, "Beginner"));
        }
        okSkills30.setSkills(skills30);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okSkills30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(30));
    }

    @Test
    void putAvailabilityLimit21() throws Exception {
        UpdateUserProfileRequest badAvail22 = validProfileRequest();
        List<AvailabilitySlot> list22 = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            list22.add(new AvailabilitySlot("Monday", "Morning"));
        }
        badAvail22.setAvailability(list22);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badAvail22)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("availability must not exceed 21 entries"));

        UpdateUserProfileRequest dupAvail = validProfileRequest();
        dupAvail.setAvailability(List.of(
                new AvailabilitySlot("Monday", "Morning"),
                new AvailabilitySlot("Monday", "Morning")
        ));
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dupAvail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Duplicate availability pair: Monday Morning"));
    }

    @Test
    void putInterestsLimit20() throws Exception {
        UpdateUserProfileRequest badInt21 = validProfileRequest();
        List<String> int21 = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            int21.add("Interest" + i);
        }
        badInt21.setInterests(int21);
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badInt21)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("interests must not exceed 20 items"));
    }

    @Test
    void putUnknownFieldsReturn400() throws Exception {
        List<String> unknownFields = List.of("email", "role", "pastEventsCount", "status");

        for (String field : unknownFields) {
            Map<String, Object> badMap = new HashMap<>(objectMapper.convertValue(validProfileRequest(), Map.class));
            badMap.put(field, "unexpected_val");
            mockMvc.perform(put("/api/me/profile")
                            .cookie(new Cookie("eventra_token", volunteerAToken))
                            .header(CSRF_HEADER, CSRF_VALUE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badMap)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Unknown field"));
        }
    }

    @Test
    void putTextRulesRejectBadInput() throws Exception {
        // Control char in fullName -> 400
        UpdateUserProfileRequest ctrlName = validProfileRequest();
        ctrlName.setFullName("Bad\u0000Name");
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ctrlName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("fullName contains invalid characters"));

        // Bad notification preference -> 400
        UpdateUserProfileRequest badNotif = validProfileRequest();
        badNotif.setNotificationPreference("sms");
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badNotif)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("notificationPreference must be email or whatsapp"));

        // Null nested item: skill name -> 400
        UpdateUserProfileRequest nullSkill = validProfileRequest();
        nullSkill.setSkills(Collections.singletonList(new SkillLevel(null, "Beginner")));
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullSkill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Skill name and level are required"));

        // Null nested item: availability slot -> 400
        UpdateUserProfileRequest nullAvail = validProfileRequest();
        nullAvail.setAvailability(Collections.singletonList(new AvailabilitySlot("Monday", null)));
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullAvail)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Availability day and slot are required"));

        // Malformed JSON -> 400
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearOfStudy\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON request body"));
    }

    @Test
    void putChangesOnlyUsersRowNotRegistrations() throws Exception {
        Event event = new Event();
        event.setName("Snapshot Event");
        event.setStatus("planning");
        event.setCreatedBy(organiser.getId());
        event = eventRepository.save(event);
        createdEventIds.add(event.getId());

        Volunteer regA = new Volunteer();
        regA.setEventId(event.getId());
        regA.setUserId(volunteerA.getId());
        regA.setFullName("Initial Registration Name");
        regA.setEmail(volunteerA.getEmail());
        regA.setRegisteredAt(Instant.now());
        regA = volunteerRepository.save(regA);

        UpdateUserProfileRequest updateReq = validProfileRequest();
        updateReq.setFullName("Brand New Name");
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Brand New Name"));

        // Volunteer B's profile is completely unchanged
        User freshB = userRepository.findById(volunteerB.getId()).orElseThrow();
        assertEquals(volunteerB.getFullName(), freshB.getFullName());

        // Volunteer A's existing registration row is NOT updated
        Volunteer freshRegA = volunteerRepository.findById(regA.getId()).orElseThrow();
        assertEquals("Initial Registration Name", freshRegA.getFullName(),
                "Existing volunteer registration must remain untouched when user profile is updated");
    }

    @Test
    void putSetsUpdatedAt() throws Exception {
        User user = userRepository.findById(volunteerA.getId()).orElseThrow();
        Instant initialUpdatedAt = user.getUpdatedAt();
        assertNotNull(initialUpdatedAt);

        Thread.sleep(50); // small delay to verify time advancement

        UpdateUserProfileRequest updateReq = validProfileRequest();
        updateReq.setFullName("Name After Delay");
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        User freshUser = userRepository.findById(volunteerA.getId()).orElseThrow();
        assertTrue(freshUser.getUpdatedAt().isAfter(initialUpdatedAt),
                "updated_at must be later after profile PUT");
    }

    @Test
    void organiserGets403() throws Exception {
        mockMvc.perform(get("/api/me/profile")
                        .cookie(new Cookie("eventra_token", organiserToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", organiserToken))
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProfileRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mockMvc.perform(get("/api/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(put("/api/me/profile")
                        .header(CSRF_HEADER, CSRF_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProfileRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void missingCsrfHeaderRejected() throws Exception {
        mockMvc.perform(put("/api/me/profile")
                        .cookie(new Cookie("eventra_token", volunteerAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validProfileRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Missing or invalid X-Requested-With header"));
    }
}
