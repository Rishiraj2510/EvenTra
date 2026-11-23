-- V4__feedback.sql
-- EvenTra feedback: organiser rates volunteers per assignment
-- PostgreSQL 16 | uuid via gen_random_uuid() | all timestamps are timestamptz | default now()

-- -------------------------------------------------------------------------
-- feedback
-- -------------------------------------------------------------------------
CREATE TABLE feedback (
    id              uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id   uuid            NOT NULL UNIQUE REFERENCES assignments (id),
    rating          smallint        NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment         varchar(300)    NULL,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now()
);

