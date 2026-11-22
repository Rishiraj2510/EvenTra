-- V3__plans_assignments.sql
-- EvenTra plan storage: plans and assignments tables
-- Missing plan row = status "none"; summary and skill gaps are computed on read.
-- NO ACTION (plain, not deferrable) blocks deleting a task or volunteer that still has assignments. EventService.delete already refuses events that have tasks or volunteers (409), so an event with assignments cannot be deleted through the app.
-- PostgreSQL 16 | uuid via gen_random_uuid() | all timestamps are timestamptz | default now()

-- -------------------------------------------------------------------------
-- plans
-- -------------------------------------------------------------------------
CREATE TABLE plans (
    id              uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid            NOT NULL UNIQUE REFERENCES events (id) ON DELETE CASCADE,
    status          text            NOT NULL CHECK (status IN ('draft', 'approved', 'published')),
    stale           boolean         NOT NULL DEFAULT false,
    generated_at    timestamptz     NOT NULL DEFAULT now(),
    approved_at     timestamptz     NULL,
    published_at    timestamptz     NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now()
);

-- -------------------------------------------------------------------------
-- assignments
-- -------------------------------------------------------------------------
CREATE TABLE assignments (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id             uuid            NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    task_id             uuid            NOT NULL REFERENCES tasks (id),
    volunteer_id        uuid            NOT NULL REFERENCES volunteers (id),
    slot_number         int             NOT NULL CHECK (slot_number >= 1),
    fit                 numeric(6,3)    NOT NULL CHECK (fit BETWEEN 0 AND 100),
    skill_score         numeric(6,3)    NOT NULL CHECK (skill_score BETWEEN 0 AND 100),
    experience_score    numeric(6,3)    NOT NULL CHECK (experience_score BETWEEN 0 AND 100),
    performance_score   numeric(6,3)    NOT NULL CHECK (performance_score BETWEEN 0 AND 100),
    load_score          numeric(6,3)    NOT NULL CHECK (load_score BETWEEN 0 AND 100),
    interest_score      numeric(6,3)    NOT NULL CHECK (interest_score BETWEEN 0 AND 100),
    reason              text            NOT NULL,
    flags               jsonb           NOT NULL DEFAULT '[]'::jsonb,
    locked              boolean         NOT NULL DEFAULT false,
    UNIQUE (plan_id, volunteer_id),
    UNIQUE (plan_id, task_id, slot_number)
);

CREATE INDEX assignments_task_id_idx ON assignments (task_id);
CREATE INDEX assignments_volunteer_id_idx ON assignments (volunteer_id);
