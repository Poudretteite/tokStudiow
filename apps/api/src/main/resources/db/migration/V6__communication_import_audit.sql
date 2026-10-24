-- Ogłoszenia, urządzenia push, import danych i dziennik audytu

CREATE TABLE announcements (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id         UUID         NOT NULL REFERENCES users (id),
    title             VARCHAR(200) NOT NULL,
    content           TEXT         NOT NULL,
    class_group_id    UUID         REFERENCES class_groups (id),
    field_of_study_id UUID         REFERENCES fields_of_study (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- najwyżej jeden odbiorca; oba puste = ogłoszenie dla wszystkich
    CONSTRAINT announcements_target_check CHECK (num_nonnulls(class_group_id, field_of_study_id) <= 1)
);

CREATE INDEX announcements_group_idx ON announcements (class_group_id);
CREATE INDEX announcements_field_idx ON announcements (field_of_study_id);

CREATE TABLE push_devices (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expo_push_token VARCHAR(255) NOT NULL UNIQUE,
    platform        VARCHAR(30)  NOT NULL,
    last_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT push_devices_platform_check CHECK (platform IN ('ANDROID', 'IOS'))
);

CREATE INDEX push_devices_user_idx ON push_devices (user_id);

CREATE TABLE import_templates (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    target_type VARCHAR(30)  NOT NULL,
    mapping     JSONB        NOT NULL,
    created_by  UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT import_templates_target_check CHECK (target_type IN ('STUDENTS_TO_GROUP'))
);

CREATE TABLE import_jobs (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id       UUID         REFERENCES import_templates (id),
    file_name         VARCHAR(255) NOT NULL,
    target_type       VARCHAR(30)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    mapping_suggested JSONB,
    mapping_final     JSONB,
    rows_total        INTEGER,
    rows_imported     INTEGER,
    rows_failed       INTEGER,
    created_by        UUID         NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ,

    CONSTRAINT import_jobs_target_check CHECK (target_type IN ('STUDENTS_TO_GROUP')),
    CONSTRAINT import_jobs_status_check
        CHECK (status IN ('PENDING', 'MAPPING', 'VALIDATED', 'COMPLETED', 'FAILED')),
    CONSTRAINT import_jobs_rows_check
        CHECK (rows_total >= 0 AND rows_imported >= 0 AND rows_failed >= 0)
);

CREATE TABLE audit_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID        REFERENCES users (id),
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID        NOT NULL,
    action      VARCHAR(30) NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT audit_log_action_check CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
);

CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id);
CREATE INDEX audit_log_actor_idx  ON audit_log (actor_id);

-- Dziennik audytu tylko do dopisywania: blokada UPDATE i DELETE nawet z poziomu aplikacji
CREATE FUNCTION audit_log_prevent_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_prevent_change();
