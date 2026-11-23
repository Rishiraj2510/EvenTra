package com.eventra.plan;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.registration.RegistrationForm;
import com.eventra.registration.RegistrationFormRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class Step4RefusalTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationFormRepository registrationFormRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private Cookie organiserCookie;

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("step4_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Step4 Organiser");
        organiser.setRole("organiser");
        organiser = userRepository.save(organiser);
        String token = jwtService.generateToken(organiser.getId(), organiser.getRole(), organiser.getTokenVersion());
        organiserCookie = new Cookie("eventra_token", token);
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            jdbcTemplate.update("DELETE FROM registration_forms WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM plans WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    private Event createEvent() {
        Event event = new Event();
        event.setName("Step 4 Event " + UUID.randomUUID());
        event.setDescription("Step 4 test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());
        return saved;
    }

    @Test
    void openFormWhenPlanPublished_returns409AndFormUnchanged() throws Exception {
        Event event = createEvent();

        RegistrationForm form = new RegistrationForm();
        form.setEventId(event.getId());
        form.setStatus("closed");
        form = registrationFormRepository.save(form);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        planRepository.save(plan);

        mockMvc.perform(post("/api/events/" + event.getId() + "/registration-form/open")
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is published; registration cannot be reopened"));

        RegistrationForm after = registrationFormRepository.findByEventId(event.getId()).orElseThrow();
        assertEquals("closed", after.getStatus(), "Registration form status must remain unchanged");
    }
}

