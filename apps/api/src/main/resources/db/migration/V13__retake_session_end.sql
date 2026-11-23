-- Koniec sesji poprawkowej. Oceny z poprawki (II termin, komisja) i oceny wpisane
-- po zakończeniu sesji podstawowej prowadzący poprawia sam do tego dnia + 2 dni.
-- Sesja poprawkowa może wypaść już po końcu semestru (np. na początku marca).
ALTER TABLE academic_terms ADD COLUMN retake_session_end DATE;
UPDATE academic_terms SET retake_session_end = exam_session_end;
ALTER TABLE academic_terms ALTER COLUMN retake_session_end SET NOT NULL;
ALTER TABLE academic_terms ADD CONSTRAINT academic_terms_retake_check
    CHECK (retake_session_end >= exam_session_end);
