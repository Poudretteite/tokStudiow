-- Przeglądanie dziennika od najnowszych wpisów (stronicowanie kursorem created_at + id)
CREATE INDEX audit_log_created_idx ON audit_log (created_at DESC, id DESC);
