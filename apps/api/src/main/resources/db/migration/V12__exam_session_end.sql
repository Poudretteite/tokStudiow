-- Koniec sesji egzaminacyjnej semestru, wspólny dla wszystkich.
-- Do tego dnia + 2 dni prowadzący sam poprawia swoje pomyłki w ocenach;
-- później ocenę zmienia już tylko dziekanat, z obowiązkowym komentarzem.
ALTER TABLE academic_terms ADD COLUMN exam_session_end DATE;
UPDATE academic_terms SET exam_session_end = end_date;
ALTER TABLE academic_terms ALTER COLUMN exam_session_end SET NOT NULL;
ALTER TABLE academic_terms ADD CONSTRAINT academic_terms_session_check
    CHECK (exam_session_end > start_date AND exam_session_end <= end_date);
