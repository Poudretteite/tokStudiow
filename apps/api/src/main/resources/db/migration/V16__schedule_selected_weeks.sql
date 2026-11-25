-- Plan zajęć: oprócz „co tydzień” i tygodni A/B także konkretne tygodnie semestru – dla kierunków,
-- które co tydzień mają inne zajęcia (np. medyczne), i do pominięcia przerw (np. świątecznej).
-- Tygodnie zapisane jako lista numerów („1,3,4,9”); tydzień 1 to tydzień, w którym zaczyna się semestr.

ALTER TABLE schedule_entries DROP CONSTRAINT schedule_entries_frequency_check;

ALTER TABLE schedule_entries ADD COLUMN selected_weeks VARCHAR(200);

ALTER TABLE schedule_entries ADD CONSTRAINT schedule_entries_frequency_check
    CHECK (frequency IN ('WEEKLY', 'ODD_WEEKS', 'EVEN_WEEKS', 'SELECTED_WEEKS'));

ALTER TABLE schedule_entries ADD CONSTRAINT schedule_entries_selected_weeks_check
    CHECK ((frequency = 'SELECTED_WEEKS') = (selected_weeks IS NOT NULL)
           AND (selected_weeks IS NULL OR selected_weeks ~ '^[0-9]{1,2}(,[0-9]{1,2})*$'));
