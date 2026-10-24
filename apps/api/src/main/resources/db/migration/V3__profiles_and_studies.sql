-- Profile ról (1:1 z users) i przebiegi studiów

CREATE TABLE student_profiles (
    user_id      UUID        PRIMARY KEY REFERENCES users (id),
    album_number VARCHAR(20) NOT NULL UNIQUE,
    birthdate    DATE
);

CREATE TABLE lecturer_profiles (
    user_id            UUID         PRIMARY KEY REFERENCES users (id),
    faculty_id         UUID         NOT NULL REFERENCES faculties (id),
    academic_title     VARCHAR(50),
    office_room        VARCHAR(50),
    consultation_hours VARCHAR(255)
);

CREATE INDEX lecturer_profiles_faculty_idx ON lecturer_profiles (faculty_id);

CREATE TABLE dean_office_profiles (
    user_id    UUID PRIMARY KEY REFERENCES users (id),
    faculty_id UUID NOT NULL REFERENCES faculties (id)
);

CREATE INDEX dean_office_profiles_faculty_idx ON dean_office_profiles (faculty_id);

CREATE TABLE studies (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id        UUID        NOT NULL REFERENCES student_profiles (user_id),
    field_of_study_id UUID        NOT NULL REFERENCES fields_of_study (id),
    start_year        SMALLINT    NOT NULL,
    current_semester  SMALLINT    NOT NULL,
    status            VARCHAR(30) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT studies_semester_check CHECK (current_semester >= 1),
    CONSTRAINT studies_status_check
        CHECK (status IN ('ACTIVE', 'DEAN_LEAVE', 'GRADUATED', 'REMOVED')),
    CONSTRAINT studies_unique UNIQUE (student_id, field_of_study_id)
);

CREATE INDEX studies_field_of_study_idx ON studies (field_of_study_id);
