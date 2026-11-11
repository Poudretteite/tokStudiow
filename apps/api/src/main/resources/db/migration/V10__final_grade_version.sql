-- Wersja wiersza oceny końcowej (optimistic locking w JPA).
-- Gdy dwie osoby lub dwa żądania zmieniają tę samą ocenę naraz, drugi zapis
-- kończy się błędem 409 zamiast po cichu nadpisać pierwszy (np. zatwierdzenie).
ALTER TABLE final_grades ADD COLUMN version BIGINT NOT NULL DEFAULT 0;