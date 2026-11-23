package com.eventra.certificate;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
public class CertificateConcurrencyTests {

    @TempDir
    static Path sharedTempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("eventra.certificates.dir", () -> sharedTempDir.toAbsolutePath().toString());
    }

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
    private CertificateService certificateService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User organiserUser;
    private Event event;
    private Volunteer volunteer;
    private final List<UUID> createdEventIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        User organiser = new User();
        organiser.setEmail("cert_conc_org_" + UUID.randomUUID() + "@example.com");
        organiser.setPasswordHash(passwordEncoder.encode("password"));
        organiser.setFullName("Cert Organiser");
        organiser.setRole("organiser");
        organiserUser = userRepository.save(organiser);

        event = new Event();
        event.setName("Cert Concurrency Event " + UUID.randomUUID());
        event.setDescription("Cert conc event");
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

        volunteer = new Volunteer();
        volunteer.setEventId(event.getId());
        volunteer.setFullName("Conc Vol");
        volunteer.setEmail("conc_" + UUID.randomUUID() + "@example.com");
        volunteer.setPhone("1234567890");
        volunteer.setStatus("active");
        volunteer = volunteerRepository.save(volunteer);

        Plan plan = new Plan(event.getId(), PlanStatus.PUBLISHED);
        plan.setStale(false);
        plan = planRepository.save(plan);

        Assignment assignment = new Assignment();
        assignment.setPlan(plan);
        assignment.setTask(task);
        assignment.setVolunteer(volunteer);
        assignment.setSlotNumber(1);
        assignment.setFit(BigDecimal.valueOf(90.0));
        assignment.setSkillScore(BigDecimal.valueOf(1.0));
        assignment.setExperienceScore(BigDecimal.valueOf(1.0));
        assignment.setPerformanceScore(BigDecimal.valueOf(1.0));
        assignment.setLoadScore(BigDecimal.valueOf(1.0));
        assignment.setInterestScore(BigDecimal.valueOf(1.0));
        assignment.setReason("Good fit");
        assignment.setFlags(List.of());
        assignmentRepository.save(assignment);
    }

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            jdbcTemplate.update("DELETE FROM certificates WHERE event_id = ?", eventId);
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
    }

    @Test
    void twoSimultaneousFirstUploads_endWithExactlyOneRowAndOneFileOnDiskAndNo500() throws Exception {
        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        byte[] pdf1 = "%PDF-1.4\nUploadOneContent".getBytes();
        byte[] pdf2 = "%PDF-1.4\nUploadTwoContent".getBytes();

        MockMultipartFile file1 = new MockMultipartFile("file", "first.pdf", "application/pdf", pdf1);
        MockMultipartFile file2 = new MockMultipartFile("file", "second.pdf", "application/pdf", pdf2);

        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        Callable<Void> task1 = () -> {
            barrier.await();
            try {
                certificateService.uploadCertificate(event.getId(), volunteer.getId(), file1);
                successCount.incrementAndGet();
            } catch (Throwable t) {
                exceptions.add(t);
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            barrier.await();
            try {
                certificateService.uploadCertificate(event.getId(), volunteer.getId(), file2);
                successCount.incrementAndGet();
            } catch (Throwable t) {
                exceptions.add(t);
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertTrue(successCount.get() >= 1, "At least one upload must succeed");
        assertTrue(exceptions.isEmpty(), "No 500 or deadlock should occur; lock serializes the transactions");

        List<Certificate> certs = certificateRepository.findAll().stream()
                .filter(c -> c.getEvent().getId().equals(event.getId()))
                .toList();
        assertEquals(1, certs.size(), "Must have exactly one certificate row in database");

        Certificate winningCert = certs.get(0);
        Path winningPath = storageService.resolveAndVerify(winningCert.getStorageKey());
        assertTrue(Files.exists(winningPath));

        File[] filesOnDisk = storageService.getRootDir().toFile().listFiles();
        assertNotNull(filesOnDisk);
        assertEquals(1, filesOnDisk.length, "Exactly one file should be left on disk");
    }
}
