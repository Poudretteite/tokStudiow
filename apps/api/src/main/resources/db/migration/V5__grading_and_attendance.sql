-- Elementy zaliczenia, oceny cząstkowe i końcowe, obecności

CREATE TABLE assessment_items (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    class_group_id UUID         NOT NULL REFERENCES class_groups (id),
    name           VARCHAR(100) NOT NULL,
    weight_percent NUMERIC(5,2) NOT NULL,
    order_index    SMALLINT     NOT NULL,
    planned_date   DATE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT assessment_items_weight_check CHECK (weight_percent BETWEEN 0 AND 100)
);

CREATE INDEX assessment_items_group_idx ON assessment_items (class_group_id);

CREATE TABLE partial_grades (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_enrollment_id UUID        NOT NULL REFERENCES group_enrollments (id),
    assessment_item_id  UUID        NOT NULL REFERENCES assessment_items (id),
    grade_value_id      UUID        NOT NULL REFERENCES grade_values (id),
    attempt             SMALLINT    NOT NULL DEFAULT 1,
    issued_by           UUID        NOT NULL REFERENCES users (id),
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT partial_grades_attempt_check CHECK (attempt >= 1),
    CONSTRAINT partial_grades_unique UNIQUE (group_enrollment_id, assessment_item_id, attempt)
);

CREATE INDEX partial_grades_item_idx ON partial_grades (assessment_item_id);

CREATE TABLE final_grades (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_enrollment_id UUID        NOT NULL REFERENCES group_enrollments (id),
    grade_value_id      UUID        NOT NULL REFERENCES grade_values (id),
    attempt             VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    approved_by         UUID        REFERENCES users (id),
    approved_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT final_grades_attempt_check CHECK (attempt IN ('FIRST', 'SECOND', 'COMMISSION')),
    CONSTRAINT final_grades_status_check  CHECK (status IN ('DRAFT', 'APPROVED')),
    -- APPROVED <=> zatwierdzający i data wypełnione; DRAFT <=> oba puste
    CONSTRAINT final_grades_approval_check CHECK (
        (status = 'APPROVED' AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR
        (status = 'DRAFT' AND approved_by IS NULL AND approved_at IS NULL)
    ),
    CONSTRAINT final_grades_unique UNIQUE (group_enrollment_id, attempt)
);

CREATE TABLE attendance (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    class_session_id    UUID        NOT NULL REFERENCES class_sessions (id),
    group_enrollment_id UUID        NOT NULL REFERENCES group_enrollments (id),
    status              VARCHAR(30) NOT NULL,
    recorded_by         UUID        NOT NULL REFERENCES users (id),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT attendance_status_check CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED')),
    CONSTRAINT attendance_unique UNIQUE (class_session_id, group_enrollment_id)
);

CREATE INDEX attendance_enrollment_idx ON attendance (group_enrollment_id);
