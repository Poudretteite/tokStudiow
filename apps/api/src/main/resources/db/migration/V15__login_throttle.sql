-- Ochrona przed zgadywaniem haseł: liczniki nieudanych prób osobno dla konta (adresu e-mail) i dla adresu IP.
-- Kluczem jest skrót SHA-256, więc w tabeli nie ma jawnych adresów e-mail ani numerów IP.
-- Wiersze są tymczasowe: sprzątanie co godzinę usuwa te sprzed doby, których blokada już minęła.

CREATE TABLE login_throttle (
    key_hash          VARCHAR(64) PRIMARY KEY,
    failures          INTEGER     NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    locked_until      TIMESTAMPTZ,

    CONSTRAINT login_throttle_failures_check CHECK (failures > 0)
);

CREATE INDEX login_throttle_window_idx ON login_throttle (window_started_at);
