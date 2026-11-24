-- Hasło startowe (z importu, od administratora lub po resecie) trzeba zmienić przy pierwszym logowaniu.
-- Istniejące konta nie są oznaczane – ich hasła ustawiono przed wprowadzeniem tej zasady.

ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at  TIMESTAMPTZ;
