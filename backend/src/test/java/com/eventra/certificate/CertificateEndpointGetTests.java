package com.eventra.certificate;

import com.eventra.auth.JwtService;
import com.eventra.auth.User;
import com.eventra.auth.UserRepository;
import com.eventra.certificate.storage.CertificateStorageService;
import com.eventra.event.Event;
import com.eventra.event.EventRepository;
import com.eventra.plan.Assignment;
import com.eventra.plan.AssignmentRepository;
import com.eventra.plan.Plan;
import com.eventra.plan.PlanRepository;
import com.eventra.plan.PlanStatus;
import com.eventra.task.Task;
import com.eventra.task.TaskRepository;
import com.eventra.volunteer.Volunteer;
import com.eventra.volunteer.VolunteerRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class CertificateEndpointGetTests {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "EvenTra";

    @TempDir
    static Path sharedTempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eventra.certificates.dir", () -> sharedTempDir.toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VolunteerRepository volunteerRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private CertificateRepository certificateRepository;

    @Autowired
    private CertificateStorageService storageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User organiserUser;
    private User volunteerUser1;
    private User volunteerUser2;
    private Cookie organiserCookie;
    private Cookie volunteerCookie1;
    private Cookie volunteerCookie2;

    private final List<UUID> createdEventIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("cert_get_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Cert Organiser");
        organiser.setRole("organiser");
        organiserUser = userRepository.save(organiser);
        organiserCookie = new Cookie("eventra_token", jwtService.generateToken(organiserUser.getId(), organiserUser.getRole(), organiserUser.getTokenVersion()));

        User vol1 = new User();
        vol1.setEmail("cert_get_vol1_" + UUID.randomUUID() + "@example.com");
        vol1.setPasswordHash(passwordEncoder.encode("password"));
        vol1.setFullName("Cert Vol One");
        vol1.setRole("volunteer");
        volunteerUser1 = userRepository.save(vol1);
        volunteerCookie1 = new Cookie("eventra_token", jwtService.generateToken(volunteerUser1.getId(), volunteerUser1.getRole(), volunteerUser1.getTokenVersion()));

        User vol2 = new User();
        vol2.setEmail("cert_get_vol2_" + UUID.randomUUID() + "@example.com");
        vol2.setPasswordHash(passwordEncoder.encode("password"));
        vol2.setFullName("Cert Vol Two");
        vol2.setRole("volunteer");
        volunteerUser2 = userRepository.save(vol2);
        volunteerCookie2 = new Cookie("eventra_token", jwtService.generateToken(volunteerUser2.getId(), volunteerUser2.getRole(), volunteerUser2.getTokenVersion()));
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            jdbcTemplate.update("DELETE FROM certificates WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM feedback WHERE assignment_id IN (SELECT id FROM assignments WHERE plan_id IN (SELECT id FROM plans WHERE event_id = ?))", eventId);
            jdbcTemplate.update("DELETE FROM assignments WHERE plan_id IN (SELECT id FROM plans WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM plans WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM tasks WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
        if (organiserUser != null) {
            userRepository.deleteById(organiserUser.getId());
        }
        if (volunteerUser1 != null) {
            userRepository.deleteById(volunteerUser1.getId());
        }
        if (volunteerUser2 != null) {
            userRepository.deleteById(volunteerUser2.getId());
        }
    }

    private static class TestContext {
        Event event;
        Plan plan;
        Task task;
        Volunteer vol1;
        Volunteer vol2;
        Assignment assignment1;
        Assignment assignment2;
    }

    private TestContext createPublishedSetup() {
        Event event = new Event();
        event.setName("Cert Get Event " + UUID.randomUUID());
        event.setDescription("Cert get test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        event = eventRepository.save(event);
        createdEventIds.add(event.getId());

        Task task = new Task();
        task.setEventId(event.getId());
        task.setTitle("Setup Room");
        task.setTaskDate(LocalDate.now().plusDays(2));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(2);
        task.setStatus("pending");
        task = taskRepository.save(task);

        Volunteer v1 = new Volunteer();
        v1.setEventId(event.getId());
        v1.setUserId(volunteerUser1.getId());
        v1.setFullName("Alice Cert");
        v1.setEmail("alice_" + UUID.randomUUID() + "@example.com");
        v1.setPhone("1234567890");
        v1.setStatus("active");
        v1 = volunteerRepository.save(v1);

        Volunteer v2 = new Volunteer();
        v2.setEventId(event.getId());
        v2.setUserId(volunteerUser2.getId());
        v2.setFullName("Bob Cert");
        v2.setEmail("bob_" + UUID.randomUUID() + "@example.com");
        v2.setPhone("9876543210");
        v2.setStatus("active");
        v2 = volunteerRepository.save(v2);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Assignment a1 = new Assignment();
        a1.setPlan(plan);
        a1.setTask(task);
        a1.setVolunteer(v1);
        a1.setSlotNumber(1);
        a1.setFit(BigDecimal.valueOf(90.0));
        a1.setSkillScore(BigDecimal.valueOf(1.0));
        a1.setExperienceScore(BigDecimal.valueOf(1.0));
        a1.setPerformanceScore(BigDecimal.valueOf(1.0));
        a1.setLoadScore(BigDecimal.valueOf(1.0));
        a1.setInterestScore(BigDecimal.valueOf(1.0));
        a1.setReason("Good fit");
        a1.setFlags(List.of());
        a1 = assignmentRepository.save(a1);

        Assignment a2 = new Assignment();
        a2.setPlan(plan);
        a2.setTask(task);
        a2.setVolunteer(v2);
        a2.setSlotNumber(2);
        a2.setFit(BigDecimal.valueOf(85.0));
        a2.setSkillScore(BigDecimal.valueOf(1.0));
        a2.setExperienceScore(BigDecimal.valueOf(1.0));
        a2.setPerformanceScore(BigDecimal.valueOf(1.0));
        a2.setLoadScore(BigDecimal.valueOf(1.0));
        a2.setInterestScore(BigDecimal.valueOf(1.0));
        a2.setReason("Good fit");
        a2.setFlags(List.of());
        a2 = assignmentRepository.save(a2);

        TestContext ctx = new TestContext();
        ctx.event = event;
        ctx.plan = plan;
        ctx.task = task;
        ctx.vol1 = v1;
        ctx.vol2 = v2;
        ctx.assignment1 = a1;
        ctx.assignment2 = a2;
        return ctx;
    }

    @Test
    void getMine_returnsExactBytesContentTypeAndFixedDispositionHeader() throws Exception {
        TestContext ctx = createPublishedSetup();

        byte[] pdfBytes = "%PDF-1.4\nCertificate-Content".getBytes();
        String storageKey = UUID.randomUUID() + ".pdf";
        storageService.storeFile(storageKey, pdfBytes);

        Certificate cert = new Certificate(ctx.event, ctx.vol1, storageKey, "application/pdf", pdfBytes.length);
        certificateRepository.save(cert);

        MvcResult result = mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate.pdf\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        assertArrayEquals(pdfBytes, result.getResponse().getContentAsByteArray());
    }

    @Test
    void getMine_returnsFixedPngDispositionHeader() throws Exception {
        TestContext ctx = createPublishedSetup();

        byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        String storageKey = UUID.randomUUID() + ".png";
        storageService.storeFile(storageKey, pngBytes);

        Certificate cert = new Certificate(ctx.event, ctx.vol1, storageKey, "image/png", pngBytes.length);
        certificateRepository.save(cert);

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate.png\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void getMine_returnsFixedJpegDispositionHeader() throws Exception {
        TestContext ctx = createPublishedSetup();

        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
        String storageKey = UUID.randomUUID() + ".jpg";
        storageService.storeFile(storageKey, jpegBytes);

        Certificate cert = new Certificate(ctx.event, ctx.vol1, storageKey, "image/jpeg", jpegBytes.length);
        certificateRepository.save(cert);

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"certificate.jpg\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void getMine_404ForNoCertificate() throws Exception {
        TestContext ctx = createPublishedSetup();

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void getMine_404WhenPlanNotPublished() throws Exception {
        TestContext ctx = createPublishedSetup();

        byte[] pdfBytes = "%PDF-1.4\nTest".getBytes();
        String storageKey = UUID.randomUUID() + ".pdf";
        storageService.storeFile(storageKey, pdfBytes);

        Certificate cert = new Certificate(ctx.event, ctx.vol1, storageKey, "application/pdf", pdfBytes.length);
        certificateRepository.save(cert);

        ctx.plan.setStatus(PlanStatus.DRAFT);
        planRepository.save(ctx.plan);

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void getMine_404ForUserWithNoVolunteerRowInEvent() throws Exception {
        TestContext ctx = createPublishedSetup();

        Event emptyEvent = new Event();
        emptyEvent.setName("Empty Event " + UUID.randomUUID());
        emptyEvent.setDescription("No vols");
        emptyEvent.setStatus("open");
        emptyEvent.setStartDate(Instant.now().plusSeconds(86400));
        emptyEvent.setEndDate(Instant.now().plusSeconds(172800));
        emptyEvent = eventRepository.save(emptyEvent);
        createdEventIds.add(emptyEvent.getId());

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", emptyEvent.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));
    }

    @Test
    void getMine_volunteerNeverReceivesAnotherVolunteersFile() throws Exception {
        TestContext ctx = createPublishedSetup();

        byte[] bytes1 = "%PDF-1.4\nFile-For-Vol1".getBytes();
        String key1 = UUID.randomUUID() + ".pdf";
        storageService.storeFile(key1, bytes1);
        Certificate cert1 = new Certificate(ctx.event, ctx.vol1, key1, "application/pdf", bytes1.length);
        certificateRepository.save(cert1);

        byte[] bytes2 = "%PDF-1.4\nFile-For-Vol2".getBytes();
        String key2 = UUID.randomUUID() + ".pdf";
        storageService.storeFile(key2, bytes2);
        Certificate cert2 = new Certificate(ctx.event, ctx.vol2, key2, "application/pdf", bytes2.length);
        certificateRepository.save(cert2);

        MvcResult res1 = mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie1)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(bytes1, res1.getResponse().getContentAsByteArray());

        MvcResult res2 = mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(volunteerCookie2)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andReturn();
        assertArrayEquals(bytes2, res2.getResponse().getContentAsByteArray());
    }

    @Test
    void getMine_organiserRole_returns403() throws Exception {
        TestContext ctx = createPublishedSetup();

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMine_unauthenticated_rejected() throws Exception {
        TestContext ctx = createPublishedSetup();

        mockMvc.perform(get("/api/events/{eventId}/certificates/mine", ctx.event.getId())
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }
}
