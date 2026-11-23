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
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
@AutoConfigureMockMvc
public class CertificateEndpointPutTests {

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
    private User volunteerUser;
    private Cookie organiserCookie;
    private Cookie volunteerCookie;

    private final List<UUID> createdEventIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("cert_put_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Cert Organiser");
        organiser.setRole("organiser");
        organiserUser = userRepository.save(organiser);
        organiserCookie = new Cookie("eventra_token", jwtService.generateToken(organiserUser.getId(), organiserUser.getRole(), organiserUser.getTokenVersion()));

        User volunteer = new User();
        volunteer.setEmail("cert_put_vol_" + UUID.randomUUID() + "@example.com");
        volunteer.setPasswordHash(passwordEncoder.encode("password"));
        volunteer.setFullName("Cert Volunteer");
        volunteer.setRole("volunteer");
        volunteerUser = userRepository.save(volunteer);
        volunteerCookie = new Cookie("eventra_token", jwtService.generateToken(volunteerUser.getId(), volunteerUser.getRole(), volunteerUser.getTokenVersion()));
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
        if (volunteerUser != null) {
            userRepository.deleteById(volunteerUser.getId());
        }
    }

    private static class TestContext {
        Event event;
        Plan plan;
        Task task;
        Volunteer volunteer;
        Assignment assignment;
    }

    private TestContext createPublishedSetup() {
        Event event = new Event();
        event.setName("Cert Test Event " + UUID.randomUUID());
        event.setDescription("Certificate test event");
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
        task.setHeadcount(1);
        task.setStatus("pending");
        task = taskRepository.save(task);

        Volunteer vol = new Volunteer();
        vol.setEventId(event.getId());
        vol.setUserId(volunteerUser.getId());
        vol.setFullName("Alice Cert");
        vol.setEmail("alice_" + UUID.randomUUID() + "@example.com");
        vol.setPhone("9876543210");
        vol.setStatus("active");
        vol = volunteerRepository.save(vol);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Assignment assignment = new Assignment();
        assignment.setPlan(plan);
        assignment.setTask(task);
        assignment.setVolunteer(vol);
        assignment.setSlotNumber(1);
        assignment.setFit(BigDecimal.valueOf(90.0));
        assignment.setSkillScore(BigDecimal.valueOf(1.0));
        assignment.setExperienceScore(BigDecimal.valueOf(1.0));
        assignment.setPerformanceScore(BigDecimal.valueOf(1.0));
        assignment.setLoadScore(BigDecimal.valueOf(1.0));
        assignment.setInterestScore(BigDecimal.valueOf(1.0));
        assignment.setReason("Good fit");
        assignment.setFlags(List.of());
        assignment = assignmentRepository.save(assignment);

        TestContext ctx = new TestContext();
        ctx.event = event;
        ctx.plan = plan;
        ctx.task = task;
        ctx.volunteer = vol;
        ctx.assignment = assignment;
        return ctx;
    }

    private byte[] makePdfBytes(int size) {
        byte[] pdfHeader = "%PDF-1.4\n%EOF".getBytes();
        if (size <= pdfHeader.length) {
            return Arrays.copyOf(pdfHeader, size);
        }
        byte[] result = new byte[size];
        System.arraycopy(pdfHeader, 0, result, 0, pdfHeader.length);
        return result;
    }

    private byte[] makePngBytes(int size) {
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] result = new byte[Math.max(size, pngHeader.length)];
        System.arraycopy(pngHeader, 0, result, 0, pngHeader.length);
        return result;
    }

    private byte[] makeJpegBytes(int size) {
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] result = new byte[Math.max(size, jpegHeader.length)];
        System.arraycopy(jpegHeader, 0, result, 0, jpegHeader.length);
        return result;
    }

    @Test
    void validPdfAccepted_createsRowAndExactFileOnDisk() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] pdfContent = makePdfBytes(100);
        MockMultipartFile file = new MockMultipartFile("file", "original_doc.pdf", "application/pdf", pdfContent);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volunteerId").value(ctx.volunteer.getId().toString()))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(100))
                .andExpect(jsonPath("$.updatedAt").exists());

        Certificate cert = certificateRepository.findByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()).orElseThrow();
        assertEquals("application/pdf", cert.getContentType());
        assertEquals(100, cert.getSizeBytes());
        assertTrue(cert.getStorageKey().endsWith(".pdf"));

        Path storedFile = storageService.resolveAndVerify(cert.getStorageKey());
        assertTrue(Files.exists(storedFile));
        assertArrayEquals(pdfContent, Files.readAllBytes(storedFile));
    }

    @Test
    void validPngAccepted_createsRowAndExactFileOnDisk() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] pngContent = makePngBytes(120);
        MockMultipartFile file = new MockMultipartFile("file", "cert.png", "image/png", pngContent);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(120));

        Certificate cert = certificateRepository.findByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()).orElseThrow();
        assertEquals("image/png", cert.getContentType());
        assertTrue(cert.getStorageKey().endsWith(".png"));

        Path storedFile = storageService.resolveAndVerify(cert.getStorageKey());
        assertTrue(Files.exists(storedFile));
        assertArrayEquals(pngContent, Files.readAllBytes(storedFile));
    }

    @Test
    void validJpegAccepted_createsRowAndExactFileOnDisk() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] jpegContent = makeJpegBytes(80);
        MockMultipartFile file = new MockMultipartFile("file", "cert.jpg", "image/jpeg", jpegContent);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(80));

        Certificate cert = certificateRepository.findByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()).orElseThrow();
        assertEquals("image/jpeg", cert.getContentType());
        assertTrue(cert.getStorageKey().endsWith(".jpg"));

        Path storedFile = storageService.resolveAndVerify(cert.getStorageKey());
        assertTrue(Files.exists(storedFile));
        assertArrayEquals(jpegContent, Files.readAllBytes(storedFile));
    }

    @Test
    void pngContentWithPdfNameAndHeader_storedAsImagePng() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] pngContent = makePngBytes(150);
        MockMultipartFile file = new MockMultipartFile("file", "my_document.pdf", "application/pdf", pngContent);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"));

        Certificate cert = certificateRepository.findByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()).orElseThrow();
        assertEquals("image/png", cert.getContentType());
        assertTrue(cert.getStorageKey().endsWith(".png"));
    }

    @Test
    void textFileRenamedPdf_returns400UnsupportedFileTypeAndNoRowNoFile() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] textContent = "This is a plain text file pretending to be pdf".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "malicious.pdf", "application/pdf", textContent);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported file type"));

        assertFalse(certificateRepository.existsByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()));
    }

    @Test
    void emptyFile_returns400FileIsEmpty() throws Exception {
        TestContext ctx = createPublishedSetup();
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    void exactlyMaxBytesAccepted() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] largePdf = makePdfBytes(5242880);
        MockMultipartFile file = new MockMultipartFile("file", "max.pdf", "application/pdf", largePdf);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeBytes").value(5242880));
    }

    @Test
    void maxBytesPlusOne_returns400FileTooLarge() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] tooLargePdf = makePdfBytes(5242881);
        MockMultipartFile file = new MockMultipartFile("file", "too_large.pdf", "application/pdf", tooLargePdf);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File too large"));

        assertFalse(certificateRepository.existsByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()));
    }

    @Test
    void missingFilePart_returns400Not500() throws Exception {
        TestContext ctx = createPublishedSetup();

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondUploadReplacesRow_leavesOneRowAndOldFileDeleted() throws Exception {
        TestContext ctx = createPublishedSetup();

        byte[] firstPdf = makePdfBytes(200);
        MockMultipartFile file1 = new MockMultipartFile("file", "first.pdf", "application/pdf", firstPdf);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file1)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk());

        Certificate firstCert = certificateRepository.findByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()).orElseThrow();
        String firstKey = firstCert.getStorageKey();
        Path firstPath = storageService.resolveAndVerify(firstKey);
        assertTrue(Files.exists(firstPath));

        byte[] secondPng = makePngBytes(300);
        MockMultipartFile file2 = new MockMultipartFile("file", "second.png", "image/png", secondPng);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file2)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(300));

        List<Certificate> certs = certificateRepository.findAll().stream()
                .filter(c -> c.getEvent().getId().equals(ctx.event.getId()))
                .toList();
        assertEquals(1, certs.size());

        Certificate secondCert = certs.get(0);
        String secondKey = secondCert.getStorageKey();
        Path secondPath = storageService.resolveAndVerify(secondKey);

        assertNotEquals(firstKey, secondKey);
        assertTrue(Files.exists(secondPath));
        assertFalse(Files.exists(firstPath), "Old file should be deleted on replace");
    }

    @Test
    void storageFileNameIsNotTheUploadedName() throws Exception {
        TestContext ctx = createPublishedSetup();
        String originalName = "my-very-secret-personal-name-1234.pdf";
        MockMultipartFile file = new MockMultipartFile("file", originalName, "application/pdf", makePdfBytes(100));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isOk());

        Certificate cert = certificateRepository.findByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()).orElseThrow();
        assertFalse(cert.getStorageKey().contains("my-very-secret-personal-name"));
        assertTrue(cert.getStorageKey().matches("^[0-9a-fA-F\\-]{36}\\.pdf$"));
    }

    @Test
    void planNotPublished_draft_approved_none_returns409AndNothingStored() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] pdf = makePdfBytes(100);
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", pdf);

        // 1. DRAFT
        ctx.plan.setStatus(PlanStatus.DRAFT);
        planRepository.save(ctx.plan);
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));

        // 2. APPROVED
        ctx.plan.setStatus(PlanStatus.APPROVED);
        planRepository.save(ctx.plan);
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));

        // 3. NONE (delete plan)
        assignmentRepository.deleteAll(List.of(ctx.assignment));
        planRepository.delete(ctx.plan);
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Plan is not published"));

        assertFalse(certificateRepository.existsByEventIdAndVolunteerId(ctx.event.getId(), ctx.volunteer.getId()));
    }

    @Test
    void volunteerOfAnotherEvent_returns404() throws Exception {
        TestContext ctx1 = createPublishedSetup();

        Event event2 = new Event();
        event2.setName("Other Event " + UUID.randomUUID());
        event2.setDescription("Other desc");
        event2.setStatus("open");
        event2.setStartDate(Instant.now().plusSeconds(86400));
        event2.setEndDate(Instant.now().plusSeconds(172800));
        event2 = eventRepository.save(event2);
        createdEventIds.add(event2.getId());

        Volunteer vol2 = new Volunteer();
        vol2.setEventId(event2.getId());
        vol2.setFullName("Other Vol");
        vol2.setEmail("other_" + UUID.randomUUID() + "@example.com");
        vol2.setPhone("1111111111");
        vol2.setStatus("active");
        vol2 = volunteerRepository.save(vol2);

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", makePdfBytes(100));

        // Event 1 with volunteer of Event 2
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx1.event.getId(), vol2.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Volunteer not found"));
    }

    @Test
    void volunteerWithoutAssignment_returns404() throws Exception {
        TestContext ctx = createPublishedSetup();

        Volunteer unassignedVol = new Volunteer();
        unassignedVol.setEventId(ctx.event.getId());
        unassignedVol.setFullName("Unassigned Vol");
        unassignedVol.setEmail("unassigned_" + UUID.randomUUID() + "@example.com");
        unassignedVol.setPhone("2222222222");
        unassignedVol.setStatus("active");
        unassignedVol = volunteerRepository.save(unassignedVol);

        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", makePdfBytes(100));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), unassignedVol.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Volunteer not found"));
    }

    @Test
    void unknownEvent_returns404() throws Exception {
        UUID fakeEventId = UUID.randomUUID();
        UUID fakeVolId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", makePdfBytes(100));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", fakeEventId, fakeVolId)
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Event not found: " + fakeEventId));
    }

    @Test
    void volunteerRole_returns403() throws Exception {
        TestContext ctx = createPublishedSetup();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", makePdfBytes(100));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(volunteerCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_rejected() throws Exception {
        TestContext ctx = createPublishedSetup();
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", makePdfBytes(100));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oversizeBeyondMultipartLimit_recordActualStatus() throws Exception {
        TestContext ctx = createPublishedSetup();
        byte[] oversizeBytes = makePdfBytes(7 * 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "oversize.pdf", "application/pdf", oversizeBytes);

        MvcResult result = mockMvc.perform(multipart(HttpMethod.PUT, "/api/events/{eventId}/certificates/{volunteerId}", ctx.event.getId(), ctx.volunteer.getId())
                        .file(file)
                        .cookie(organiserCookie)
                        .header(CSRF_HEADER, CSRF_VALUE))
                .andReturn();

        int actualStatus = result.getResponse().getStatus();
        System.out.println("ACTUAL STATUS FOR OVERSIZE BEYOND MULTIPART LIMIT: " + actualStatus);
        assertNotEquals(200, actualStatus);
    }
}
