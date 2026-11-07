package com.eventra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify the Flyway-applied schema constraints
 * against the real Docker PostgreSQL database.
 */
@SpringBootTest(properties = {
        "jwt.secret=eventra_test_super_secret_key_at_least_32_bytes_long!!"
})
class SchemaConstraintTests {

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------------------
    // Helper: list all base tables visible to the current user
    // ---------------------------------------------------------------------------
    private List<String> allTables() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
    }

    // ---------------------------------------------------------------------------
    // 1. All expected tables exist
    // ---------------------------------------------------------------------------
    @Test
    void allTablesExist() {
        List<String> tables = allTables();
        for (String expected : List.of("users", "events", "tasks", "volunteers",
                                        "settings", "registration_forms")) {
            assertTrue(tables.contains(expected),
                    "Expected table '" + expected + "' not found. Found: " + tables);
        }
    }

    // ---------------------------------------------------------------------------
    // 2. Duplicate (event_id, lower(email)) volunteer insert must fail
    // ---------------------------------------------------------------------------
    @Test
    @Transactional
    void duplicateVolunteerEmailPerEventFails() {
        // Insert a minimal user and event to satisfy foreign keys
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO users (id, email, full_name, password_hash, role)
                VALUES (?::uuid, ?, 'Test User', 'hash', 'organiser')
                """, userId, "organiser_" + userId + "@test.com");

        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO events (id, name, created_by)
                VALUES (?::uuid, 'Test Event', ?::uuid)
                """, eventId, userId);

        // First volunteer — should succeed
        jdbc.update("""
                INSERT INTO volunteers (event_id, full_name, email)
                VALUES (?::uuid, 'Alice', 'alice@example.com')
                """, eventId);

        // Second volunteer with same email (different case) — must fail
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO volunteers (event_id, full_name, email)
                        VALUES (?::uuid, 'Alice Duplicate', 'Alice@Example.COM')
                        """, eventId),
                "Expected unique constraint violation for duplicate (event_id, lower(email))");
    }

    // ---------------------------------------------------------------------------
    // 3. Settings weights not summing to 100 must fail
    // ---------------------------------------------------------------------------
    @Test
    @Transactional
    void settingsWeightsSumConstraintFails() {
        jdbc.update("DELETE FROM settings");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO settings
                            (weight_skill, weight_experience, weight_performance,
                             weight_load, weight_interest, first_timer_bonus, default_max_hours)
                        VALUES (10, 10, 10, 10, 10, 5, 8)
                        """),
                "Expected CHECK or PK violation when inserting a second settings row");
    }

    // ---------------------------------------------------------------------------
    // 4. A registration_form with 3 extra questions must fail
    // ---------------------------------------------------------------------------
    @Test
    @Transactional
    void registrationFormThreeExtraQuestionsFails() {
        // Create supporting rows
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO users (id, email, full_name, password_hash, role)
                VALUES (?::uuid, ?, 'Org User', 'hash', 'organiser')
                """, userId, "org_" + userId + "@test.com");

        String eventId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO events (id, name, created_by)
                VALUES (?::uuid, 'Form Event', ?::uuid)
                """, eventId, userId);

        // Three extra questions — must violate CHECK (jsonb_array_length <= 2)
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO registration_forms (event_id, extra_questions)
                        VALUES (?::uuid, '[{"id":"q1","label":"Q1"},{"id":"q2","label":"Q2"},{"id":"q3","label":"Q3"}]'::jsonb)
                        """, eventId),
                "Expected CHECK violation for more than 2 extra questions");
    }

    // ---------------------------------------------------------------------------
    // 5. A second settings row must fail (single-row enforcement)
    // ---------------------------------------------------------------------------
    @Test
    void secondSettingsRowFails() {
        // only_row PK is fixed to true; a second INSERT must fail on PK conflict
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO settings
                            (weight_skill, weight_experience, weight_performance,
                             weight_load, weight_interest, first_timer_bonus, default_max_hours)
                        VALUES (45, 15, 15, 15, 10, 5, 8)
                        """),
                "Expected PRIMARY KEY violation when inserting a second settings row");
    }
}

