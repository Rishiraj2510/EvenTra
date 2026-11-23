-- V5__certificates.sql
-- EvenTra certificates: organiser uploads one certificate per volunteer per event; volunteer downloads their own
-- PostgreSQL 16 | uuid via gen_random_uuid() | all timestamps are timestamptz | default now()

-- -------------------------------------------------------------------------
-- certificates
-- -------------------------------------------------------------------------
CREATE TABLE certificates (
    id              uuid            PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid            NOT NULL REFERENCES events (id),
    volunteer_id    uuid            NOT NULL REFERENCES volunteers (id),
    storage_key     varchar(100)    NOT NULL UNIQUE,
    content_type    varchar(30)     NOT NULL CHECK (content_type IN ('application/pdf', 'image/png', 'image/jpeg')),
    size_bytes      integer         NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    created_at      timestamptz     NOT NULL DEFAULT now(),
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    CONSTRAINT uq_certificates_event_volunteer UNIQUE (event_id, volunteer_id)
);

