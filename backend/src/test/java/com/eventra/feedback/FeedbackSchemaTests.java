package com.eventra.feedback;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!",
        "jwt.expiry-minutes=30"
})
public class FeedbackSchemaTests {

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
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> createdEventIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (UUID eventId : createdEventIds) {
            jdbcTemplate.update("DELETE FROM feedback WHERE assignment_id IN (SELECT id FROM assignments WHERE plan_id IN (SELECT id FROM plans WHERE event_id = ?))", eventId);
            jdbcTemplate.update("DELETE FROM assignments WHERE plan_id IN (SELECT id FROM plans WHERE event_id = ?)", eventId);
            jdbcTemplate.update("DELETE FROM plans WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM tasks WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM volunteers WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM events WHERE id = ?", eventId);
        }
        createdEventIds.clear();
    }

    private Assignment createAssignment() {
        Event event = new Event();
        event.setName("Schema Event " + UUID.randomUUID());
        event.setDescription("Schema test event");
        event.setStatus("open");
        event.setStartDate(Instant.now().plusSeconds(86400));
        event.setEndDate(Instant.now().plusSeconds(172800));
        Event saved = eventRepository.save(event);
        createdEventIds.add(saved.getId());

        Task task = new Task();
        task.setEventId(saved.getId());
        task.setTitle("Task");
        task.setTaskDate(LocalDate.now().plusDays(2));
        task.setStartTime(LocalTime.of(9, 0));
        task.setEndTime(LocalTime.of(12, 0));
        task.setHeadcount(1);
        task.setStatus("pending");
        task = taskRepository.save(task);

        Volunteer v = new Volunteer();
        v.setEventId(saved.getId());
        v.setFullName("Schema Volunteer");
        v.setEmail("schema" + UUID.randomUUID() + "@example.com");
        v.setPhone("9876543210");
        v.setStatus("active");
        v = volunteerRepository.save(v);

        Plan plan = new Plan(saved.getId(), PlanStatus.PUBLISHED);
        plan = planRepository.save(plan);

        Assignment a = new Assignment();
        a.setPlan(plan);
        a.setTask(task);
        a.setVolunteer(v);
        a.setSlotNumber(1);
        a.setFit(BigDecimal.valueOf(90.0));
        a.setSkillScore(BigDecimal.valueOf(1.0));
        a.setExperienceScore(BigDecimal.valueOf(1.0));
        a.setPerformanceScore(BigDecimal.valueOf(1.0));
        a.setLoadScore(BigDecimal.valueOf(1.0));
        a.setInterestScore(BigDecimal.valueOf(1.0));
        a.setReason("Fit");
        a.setFlags(List.of());
        return assignmentRepository.save(a);
    }

    @Test
    void rejectsRating0() {
        Assignment a = createAssignment();
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("INSERT INTO feedback (id, assignment_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                    UUID.randomUUID(), a.getId(), 0, "Test");
        });
    }

    @Test
    void rejectsRating6() {
        Assignment a = createAssignment();
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("INSERT INTO feedback (id, assignment_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                    UUID.randomUUID(), a.getId(), 6, "Test");
        });
    }

    @Test
    void rejectsCommentOver300() {
        Assignment a = createAssignment();
        String comment301 = "a".repeat(301);
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("INSERT INTO feedback (id, assignment_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                    UUID.randomUUID(), a.getId(), 5, comment301);
        });
    }

    @Test
    void rejectsSecondFeedbackRowForSameAssignment() {
        Assignment a = createAssignment();
        jdbcTemplate.update("INSERT INTO feedback (id, assignment_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                UUID.randomUUID(), a.getId(), 5, "First feedback");

        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update("INSERT INTO feedback (id, assignment_id, rating, comment, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                    UUID.randomUUID(), a.getId(), 4, "Second feedback");
        });
    }
}

