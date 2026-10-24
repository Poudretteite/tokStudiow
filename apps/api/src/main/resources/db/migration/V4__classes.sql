-- Grupy zajęciowe, zapisy, plan i konkretne zajęcia

CREATE TABLE class_groups (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_component_id UUID        NOT NULL REFERENCES course_components (id),
    academic_term_id    UUID        NOT NULL REFERENCES academic_terms (id),
    lecturer_id         UUID        NOT NULL REFERENCES lecturer_profiles (user_id),
    group_number        SMALLINT    NOT NULL,
    capacity            SMALLINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT class_groups_capacity_check CHECK (capacity > 0),
    CONSTRAINT class_groups_unique UNIQUE (course_component_id, academic_term_id, group_number)
);

CREATE INDEX class_groups_term_idx     ON class_groups (academic_term_id);
CREATE INDEX class_groups_lecturer_idx ON class_groups (lecturer_id);

CREATE TABLE group_enrollments (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    class_group_id UUID        NOT NULL REFERENCES class_groups (id),
    study_id       UUID        NOT NULL REFERENCES studies (id),
    enrolled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT group_enrollments_unique UNIQUE (class_group_id, study_id)
);

CREATE INDEX group_enrollments_study_idx ON group_enrollments (study_id);

CREATE TABLE schedule_entries (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    class_group_id UUID        NOT NULL REFERENCES class_groups (id),
    room_id        UUID        NOT NULL REFERENCES rooms (id),
    day_of_week    SMALLINT    NOT NULL,
    start_time     TIME        NOT NULL,
    end_time       TIME        NOT NULL,
    frequency      VARCHAR(30) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT schedule_entries_day_check   CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT schedule_entries_time_check  CHECK (end_time > start_time),
    CONSTRAINT schedule_entries_frequency_check
        CHECK (frequency IN ('WEEKLY', 'ODD_WEEKS', 'EVEN_WEEKS'))
);

CREATE INDEX schedule_entries_group_idx ON schedule_entries (class_group_id);
CREATE INDEX schedule_entries_room_idx  ON schedule_entries (room_id);

CREATE TABLE class_sessions (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    class_group_id    UUID        NOT NULL REFERENCES class_groups (id),
    schedule_entry_id UUID        REFERENCES schedule_entries (id),
    room_id           UUID        REFERENCES rooms (id),
    date              DATE        NOT NULL,
    start_time        TIME        NOT NULL,
    end_time          TIME        NOT NULL,
    is_cancelled      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT class_sessions_time_check CHECK (end_time > start_time)
);

CREATE INDEX class_sessions_group_date_idx ON class_sessions (class_group_id, date);
CREATE INDEX class_sessions_entry_idx      ON class_sessions (schedule_entry_id);
