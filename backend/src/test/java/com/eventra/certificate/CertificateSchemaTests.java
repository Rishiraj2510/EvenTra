package com.eventra.certificate;

import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
public class CertificateSchemaTests {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();
    private Event event;
    private Volunteer volunteer;

    @BeforeEach
    void setUp() {
        Event evt = new Event();
        evt.setName("Schema Test Event " + UUID.randomUUID());
        evt.setDescription("Cert schema test");
        evt.setStatus("open");
        evt.setStartDate(Instant.now().plusSeconds(86400));
        evt.setEndDate(Instant.now().plusSeconds(172800));
        event = eventRepository.save(evt);
        createdEventIds.add(event.getId());

        Volunteer vol = new Volunteer();
        vol.setEventId(event.getId());
        vol.setFullName("Schema Vol");
        vol.setEmail("vol_" + UUID.randomUUID() + "@example.com");
        vol.setPhone("1234567890");
        vol.setStatus("active");
        volunteer = volunteerRepository.save(vol);
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            jdbcTemplate.update("DELETE FROM certificates WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    @Test
    void rejectsInvalidContentType_textPlain() {
        Certificate cert = new Certificate(event, volunteer, UUID.randomUUID().toString(), "text/plain", 100);
        assertThrows(DataIntegrityViolationException.class, () -> certificateRepository.saveAndFlush(cert));
    }

    @Test
    void rejectsSizeZero() {
        Certificate cert = new Certificate(event, volunteer, UUID.randomUUID().toString(), "application/pdf", 0);
        assertThrows(DataIntegrityViolationException.class, () -> certificateRepository.saveAndFlush(cert));
    }

    @Test
    void rejectsSizeTooLarge_5242881() {
        Certificate cert = new Certificate(event, volunteer, UUID.randomUUID().toString(), "application/pdf", 5242881);
        assertThrows(DataIntegrityViolationException.class, () -> certificateRepository.saveAndFlush(cert));
    }

    @Test
    void rejectsDuplicateEventAndVolunteer() {
        Certificate cert1 = new Certificate(event, volunteer, UUID.randomUUID().toString(), "application/pdf", 100);
        certificateRepository.saveAndFlush(cert1);

        Certificate cert2 = new Certificate(event, volunteer, UUID.randomUUID().toString(), "application/pdf", 200);
        assertThrows(DataIntegrityViolationException.class, () -> certificateRepository.saveAndFlush(cert2));
    }
}
