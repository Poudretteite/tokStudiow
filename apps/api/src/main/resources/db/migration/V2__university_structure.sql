-- Struktura uczelni, skale ocen i program studiów

CREATE TABLE faculties (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(200) NOT NULL UNIQUE,
    short_name VARCHAR(20)  NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE fields_of_study (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    faculty_id      UUID         NOT NULL REFERENCES faculties (id),
    name            VARCHAR(200) NOT NULL,
    level           VARCHAR(30)  NOT NULL,
    mode            VARCHAR(30)  NOT NULL,
    semesters_count SMALLINT     NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fields_of_study_level_check CHECK (level IN ('FIRST_CYCLE', 'SECOND_CYCLE')),
    CONSTRAINT fields_of_study_mode_check  CHECK (mode IN ('FULL_TIME', 'PART_TIME')),
    CONSTRAINT fields_of_study_semesters_check CHECK (semesters_count > 0),
    CONSTRAINT fields_of_study_unique UNIQUE (faculty_id, name, level, mode)
);

CREATE TABLE academic_terms (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(50) NOT NULL UNIQUE,
    type       VARCHAR(30) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    is_current BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT academic_terms_type_check  CHECK (type IN ('WINTER', 'SUMMER')),
    CONSTRAINT academic_terms_dates_check CHECK (end_date > start_date)
);

-- Najwyżej jeden bieżący semestr
CREATE UNIQUE INDEX academic_terms_one_current ON academic_terms (is_current) WHERE is_current;

CREATE TABLE rooms (
    id       UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    building VARCHAR(50) NOT NULL,
    number   VARCHAR(20) NOT NULL,
    capacity SMALLINT,

    CONSTRAINT rooms_capacity_check CHECK (capacity > 0),
    CONSTRAINT rooms_unique UNIQUE (building, number)
);

CREATE TABLE grading_scales (
    id   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(30)  NOT NULL,

    CONSTRAINT grading_scales_type_check CHECK (type IN ('NUMERIC', 'PASS_FAIL'))
);

CREATE TABLE grade_values (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    grading_scale_id UUID         NOT NULL REFERENCES grading_scales (id),
    label            VARCHAR(20)  NOT NULL,
    numeric_value    NUMERIC(3,1),
    is_passing       BOOLEAN      NOT NULL,
    sort_order       SMALLINT     NOT NULL,

    CONSTRAINT grade_values_unique UNIQUE (grading_scale_id, label)
);

CREATE TABLE courses (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    field_of_study_id UUID         NOT NULL REFERENCES fields_of_study (id),
    code              VARCHAR(30)  NOT NULL UNIQUE,
    name              VARCHAR(200) NOT NULL,
    ects              SMALLINT     NOT NULL,
    semester_number   SMALLINT     NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT courses_ects_check     CHECK (ects >= 0),
    CONSTRAINT courses_semester_check CHECK (semester_number >= 1)
);

CREATE INDEX courses_field_of_study_idx ON courses (field_of_study_id);

CREATE TABLE course_components (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id        UUID        NOT NULL REFERENCES courses (id),
    type             VARCHAR(30) NOT NULL,
    hours            SMALLINT    NOT NULL,
    grading_scale_id UUID        NOT NULL REFERENCES grading_scales (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT course_components_type_check
        CHECK (type IN ('LECTURE', 'EXERCISES', 'LAB', 'SEMINAR', 'PROJECT')),
    CONSTRAINT course_components_hours_check CHECK (hours > 0),
    CONSTRAINT course_components_unique UNIQUE (course_id, type)
);
