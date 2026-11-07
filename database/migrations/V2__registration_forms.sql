-- V2__registration_forms.sql
-- Registration form configuration per event

CREATE TABLE registration_forms (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        uuid        NOT NULL UNIQUE REFERENCES events (id) ON DELETE CASCADE,
    status          text        NOT NULL DEFAULT 'draft'
                                CHECK (status IN ('draft', 'open', 'closed')),
    opens_at        timestamptz,
    closes_at       timestamptz,
    max_responses   int         CHECK (max_responses >= 1),
    offered_skills  jsonb       NOT NULL DEFAULT '[]',
    extra_questions jsonb       NOT NULL DEFAULT '[]',
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    -- closes_at must be after opens_at when both are provided
    CHECK (closes_at > opens_at OR closes_at IS NULL OR opens_at IS NULL),
    -- At most 2 extra questions allowed
    CHECK (jsonb_array_length(extra_questions) <= 2)
);

