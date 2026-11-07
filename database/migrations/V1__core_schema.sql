-- V1__core_schema.sql
-- EvenTra core schema: users, events, tasks, volunteers, settings
-- PostgreSQL 16 | uuid via gen_random_uuid() | all timestamps are timestamptz | default now()

-- -------------------------------------------------------------------------
-- users
-- -------------------------------------------------------------------------
CREATE TABLE users (
    id                      uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   text            NOT NULL,
    full_name               text            NOT NULL,
    password_hash           text            NOT NULL,
    role                    text            NOT NULL CHECK (role IN ('organiser', 'volunteer')),
    token_version           int             NOT NULL DEFAULT 0,
    phone                   text,
    department              text,
    year_of_study           int             CHECK (year_of_study BETWEEN 1 AND 5),
    skills                  jsonb           NOT NULL DEFAULT '[]',
    past_events_count       int             NOT NULL DEFAULT 0 CHECK (past_events_count >= 0),
    availability            jsonb           NOT NULL DEFAULT '[]',
    max_hours               int             CHECK (max_hours >= 1),
    interests               jsonb           NOT NULL DEFAULT '[]',
    notification_preference text            CHECK (notification_preference IN ('email', 'whatsapp')),
    created_at              timestamptz     NOT NULL DEFAULT now(),
    updated_at              timestamptz     NOT NULL DEFAULT now()
);

-- Case-insensitive unique email
CREATE UNIQUE INDEX users_email_lower_idx ON users (lower(email));

-- -------------------------------------------------------------------------
-- events
-- -------------------------------------------------------------------------
CREATE TABLE events (
    id                      uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    text            NOT NULL,
    description             text,
    start_date              timestamptz,
    end_date                timestamptz,
    status                  text,
    created_by              uuid            REFERENCES users (id) ON DELETE SET NULL,
    certificate_template    jsonb,
    created_at              timestamptz     NOT NULL DEFAULT now(),
    updated_at              timestamptz     NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------------------
-- tasks
-- -------------------------------------------------------------------------
CREATE TABLE tasks (
    id                      uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                uuid            NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    title                   text            NOT NULL,
    description             text,
    task_date               date,
    start_time              time,
    end_time                time,
    headcount               int             NOT NULL DEFAULT 1 CHECK (headcount >= 1),
    mandatory_skills        jsonb           NOT NULL DEFAULT '[]',
    preferred_skills        jsonb           NOT NULL DEFAULT '[]',
    critical                boolean         NOT NULL DEFAULT false,
    location                text,
    status                  text,
    created_at              timestamptz     NOT NULL DEFAULT now(),
    updated_at              timestamptz     NOT NULL DEFAULT now(),
    CHECK (end_time > start_time OR start_time IS NULL OR end_time IS NULL)
);

CREATE INDEX tasks_event_id_idx ON tasks (event_id);

-- -------------------------------------------------------------------------
-- volunteers
-- -------------------------------------------------------------------------
CREATE TABLE volunteers (
    id                      uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                uuid            NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id                 uuid            REFERENCES users (id) ON DELETE SET NULL,
    full_name               text            NOT NULL,
    email                   text            NOT NULL,
    phone                   text,
    department              text,
    year_of_study           int             CHECK (year_of_study BETWEEN 1 AND 5),
    skills                  jsonb           NOT NULL DEFAULT '[]',
    past_events_count       int             NOT NULL DEFAULT 0 CHECK (past_events_count >= 0),
    availability            jsonb           NOT NULL DEFAULT '[]',
    max_hours_per_event     int             CHECK (max_hours_per_event >= 1),
    interests               jsonb           NOT NULL DEFAULT '[]',
    notification_preference text            CHECK (notification_preference IN ('email', 'whatsapp')),
    status                  text,
    extra_answers           jsonb           NOT NULL DEFAULT '{}',
    registered_at           timestamptz     NOT NULL DEFAULT now()
);

-- Unique: one registration per authenticated user per event (nulls excluded)
CREATE UNIQUE INDEX volunteers_event_user_idx
    ON volunteers (event_id, user_id)
    WHERE user_id IS NOT NULL;

-- Unique: one registration per email (case-insensitive) per event
CREATE UNIQUE INDEX volunteers_event_email_lower_idx
    ON volunteers (event_id, lower(email));

-- Index on event_id for fast lookups
CREATE INDEX volunteers_event_id_idx ON volunteers (event_id);

-- -------------------------------------------------------------------------
-- settings  (single-row table enforced by a fixed boolean PK)
-- -------------------------------------------------------------------------
CREATE TABLE settings (
    only_row                boolean         PRIMARY KEY DEFAULT true,
    weight_skill            int             NOT NULL CHECK (weight_skill    >= 0),
    weight_experience       int             NOT NULL CHECK (weight_experience >= 0),
    weight_performance      int             NOT NULL CHECK (weight_performance >= 0),
    weight_load             int             NOT NULL CHECK (weight_load     >= 0),
    weight_interest         int             NOT NULL CHECK (weight_interest >= 0),
    first_timer_bonus       int             NOT NULL CHECK (first_timer_bonus >= 0),
    default_max_hours       int             NOT NULL CHECK (default_max_hours >= 1),
    -- The five solver weights must sum exactly to 100
    CHECK (weight_skill + weight_experience + weight_performance + weight_load + weight_interest = 100),
    -- Enforce only a single row: the PK must always be true
    CHECK (only_row = true)
);

-- Default configuration row (45+15+15+15+10 = 100)
INSERT INTO settings
    (weight_skill, weight_experience, weight_performance, weight_load, weight_interest,
     first_timer_bonus, default_max_hours)
VALUES
    (45, 15, 15, 15, 10, 5, 8);

