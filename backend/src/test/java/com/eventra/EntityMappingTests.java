package com.eventra;

import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.model.AvailabilitySlot;
import com.eventra.model.ExtraQuestion;
import com.eventra.model.SkillLevel;
import com.eventra.registration.RegistrationForm;
import com.eventra.registration.RegistrationFormRepository;
import com.eventra.settings.Settings;
import com.eventra.settings.SettingsRepository;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!"
})
class EntityMappingTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private RegistrationFormRepository registrationFormRepository;

    @Autowired
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // 1. Application context starts with ddl-auto validate (context loads)
    // -------------------------------------------------------------------------
    @Test
    void contextLoadsAndSchemaValidates() {
        assertNotNull(userRepository);
        assertNotNull(eventRepository);
        assertNotNull(taskRepository);
        assertNotNull(volunteerRepository);
        assertNotNull(settingsRepository);
        assertNotNull(registrationFormRepository);
    }

    // -------------------------------------------------------------------------
    // 2. Save and reload a User, Event, Task with jsonb lists; assert round-trip
    // -------------------------------------------------------------------------
    @Test
    @Transactional
    void userEventTaskJsonbListsRoundTrip() {
        // User
        User user = new User();
        user.setEmail("test_organizer@example.com");
        user.setFullName("Test Organizer");
        user.setPasswordHash("hashed_pw");
        user.setRole("organiser");
        user.setInterests(List.of("Robotics", "AI"));
        user = userRepository.saveAndFlush(user);

        // Event
        Event event = new Event();
        event.setName("Tech Symposium 2026");
        event.setCreatedBy(user.getId());
        event.setCertificateTemplate(Map.of("templateId", "tpl-123", "theme", "blue"));
        event = eventRepository.saveAndFlush(event);

        // Task
        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle("Stage Setup");
        task.setTaskDate(LocalDate.of(2026, 11, 15));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setMandatorySkills(List.of("Audio", "Lighting"));
        task.setPreferredSkills(List.of("Carpentry"));
        task = taskRepository.saveAndFlush(task);

        entityManager.flush();
        entityManager.clear();

        // Reload and verify
        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(List.of("Robotics", "AI"), reloadedUser.getInterests());

        Event reloadedEvent = eventRepository.findById(event.getId()).orElseThrow();
        assertEquals("tpl-123", reloadedEvent.getCertificateTemplate().get("templateId"));

        Task reloadedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(List.of("Audio", "Lighting"), reloadedTask.getMandatorySkills());
        assertEquals(List.of("Carpentry"), reloadedTask.getPreferredSkills());
    }

    // -------------------------------------------------------------------------
    // 3. Save and reload a Volunteer with skills, availability, extraAnswers;
    //    assert equality after clear
    // -------------------------------------------------------------------------
    @Test
    @Transactional
    void volunteerComplexJsonbRoundTrip() {
        Event event = new Event();
        event.setName("Annual Fest");
        event = eventRepository.saveAndFlush(event);

        Volunteer volunteer = new Volunteer();
        volunteer.setEventId(event.getId());
        volunteer.setFullName("Bob Volunteer");
        volunteer.setEmail("bob@example.com");
        volunteer.setSkills(List.of(
                new SkillLevel("Audio", "Advanced"),
                new SkillLevel("First Aid", "Intermediate")
        ));
        volunteer.setAvailability(List.of(
                new AvailabilitySlot("Monday", "Morning"),
                new AvailabilitySlot("Wednesday", "Evening")
        ));
        volunteer.setExtraAnswers(Map.of("tshirt_size", "L", "dietary", "Vegan"));

        volunteer = volunteerRepository.saveAndFlush(volunteer);
        assertNotNull(volunteer.getRegisteredAt());

        entityManager.flush();
        entityManager.clear();

        Volunteer reloaded = volunteerRepository.findById(volunteer.getId()).orElseThrow();
        assertEquals(2, reloaded.getSkills().size());
        assertEquals(new SkillLevel("Audio", "Advanced"), reloaded.getSkills().get(0));
        assertEquals(new SkillLevel("First Aid", "Intermediate"), reloaded.getSkills().get(1));

        assertEquals(2, reloaded.getAvailability().size());
        assertEquals(new AvailabilitySlot("Monday", "Morning"), reloaded.getAvailability().get(0));
        assertEquals(new AvailabilitySlot("Wednesday", "Evening"), reloaded.getAvailability().get(1));

        assertEquals("L", reloaded.getExtraAnswers().get("tshirt_size"));
        assertEquals("Vegan", reloaded.getExtraAnswers().get("dietary"));
    }

    // -------------------------------------------------------------------------
    // 4. updated_at changes after an update
    // -------------------------------------------------------------------------
    @Test
    @Transactional
    void updatedAtChangesAfterUpdate() throws Exception {
        User user = new User();
        user.setEmail("timestamp_test@example.com");
        user.setFullName("Timestamp Tester");
        user.setPasswordHash("hashed_pw");
        user.setRole("volunteer");
        user = userRepository.saveAndFlush(user);

        Instant originalCreatedAt = user.getCreatedAt();
        Instant originalUpdatedAt = user.getUpdatedAt();
        assertNotNull(originalCreatedAt);
        assertNotNull(originalUpdatedAt);

        // Sleep briefly to ensure instant increment on database clock
        Thread.sleep(50);

        user.setFullName("Timestamp Tester Updated");
        user = userRepository.saveAndFlush(user);

        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("Timestamp Tester Updated", reloaded.getFullName());
        assertEquals(originalCreatedAt, reloaded.getCreatedAt());
        assertFalse(reloaded.getUpdatedAt().isBefore(originalUpdatedAt));
    }

    // -------------------------------------------------------------------------
    // 5. Saving a second Volunteer with same eventId and email in different case throws DataIntegrityViolationException
    // -------------------------------------------------------------------------
    @Test
    @Transactional
    void duplicateVolunteerEventAndEmailThrows() {
        Event event = new Event();
        event.setName("Duplicate Check Event");
        event = eventRepository.saveAndFlush(event);

        Volunteer v1 = new Volunteer();
        v1.setEventId(event.getId());
        v1.setFullName("Charlie");
        v1.setEmail("charlie@college.edu");
        volunteerRepository.saveAndFlush(v1);

        Volunteer v2 = new Volunteer();
        v2.setEventId(event.getId());
        v2.setFullName("Charlie Copy");
        v2.setEmail("CHARLIE@COLLEGE.EDU");

        assertThrows(DataIntegrityViolationException.class, () -> {
            volunteerRepository.saveAndFlush(v2);
        });
    }

    // -------------------------------------------------------------------------
    // 6. Settings: findAll returns exactly one row with weights summing to 100
    // -------------------------------------------------------------------------
    @Test
    @Transactional
    void settingsFindAllReturnsSingleRowSummingTo100() {
        List<Settings> allSettings = settingsRepository.findAll();
        assertEquals(1, allSettings.size(), "Settings table must have exactly one row");

        Settings s = allSettings.get(0);
        assertTrue(s.isOnlyRow());
        int sum = s.getWeightSkill()
                + s.getWeightExperience()
                + s.getWeightPerformance()
                + s.getWeightLoad()
                + s.getWeightInterest();
        assertEquals(100, sum, "Sum of solver weights must equal 100");
    }

    // -------------------------------------------------------------------------
    // 7. RegistrationForm: findWithLockByEventId returns saved form and extraQuestions round-trips
    // -------------------------------------------------------------------------
    @Test
    @Transactional
    void registrationFormFindWithLockAndExtraQuestionsRoundTrip() {
        Event event = new Event();
        event.setName("Registration Locked Event");
        event = eventRepository.saveAndFlush(event);

        RegistrationForm form = new RegistrationForm();
        form.setEventId(event.getId());
        form.setStatus("open");
        form.setOfferedSkills(List.of("Audio", "Stage Handling"));
        form.setExtraQuestions(List.of(
                new ExtraQuestion("q1", "Do you have prior experience?"),
                new ExtraQuestion("q2", "T-shirt size")
        ));
        form = registrationFormRepository.saveAndFlush(form);

        entityManager.flush();
        entityManager.clear();

        Optional<RegistrationForm> lockedForm = registrationFormRepository.findWithLockByEventId(event.getId());
        assertTrue(lockedForm.isPresent());
        RegistrationForm found = lockedForm.get();
        assertEquals("open", found.getStatus());
        assertEquals(List.of("Audio", "Stage Handling"), found.getOfferedSkills());
        assertEquals(2, found.getExtraQuestions().size());
        assertEquals(new ExtraQuestion("q1", "Do you have prior experience?"), found.getExtraQuestions().get(0));
        assertEquals(new ExtraQuestion("q2", "T-shirt size"), found.getExtraQuestions().get(1));
    }
}

