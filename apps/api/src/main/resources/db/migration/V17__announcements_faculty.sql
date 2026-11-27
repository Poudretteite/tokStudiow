-- Ogłoszenia: oprócz grupy i kierunku także cały wydział (dziekanat pisze do „swoich” studentów
-- i prowadzących). Wszystkie trzy puste = ogłoszenie dla całej uczelni (tylko administrator).

ALTER TABLE announcements ADD COLUMN faculty_id UUID REFERENCES faculties (id);

ALTER TABLE announcements DROP CONSTRAINT announcements_target_check;
ALTER TABLE announcements ADD CONSTRAINT announcements_target_check
    CHECK (num_nonnulls(class_group_id, field_of_study_id, faculty_id) <= 1);

ALTER TABLE announcements ADD CONSTRAINT announcements_title_check CHECK (length(btrim(title)) > 0);
ALTER TABLE announcements ADD CONSTRAINT announcements_content_check
    CHECK (length(btrim(content)) > 0 AND length(content) <= 5000);

CREATE INDEX announcements_faculty_idx ON announcements (faculty_id);
CREATE INDEX announcements_created_idx ON announcements (created_at DESC);
