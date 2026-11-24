package pl.tokstudiow.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Czyści bazę między testami. Leży w źródłach testowych, więc nie trafia do aplikacji:
 * wyłącza triggery (w tym ochronę audit_log) tylko na czas jednej transakcji.
 */
public class DatabaseCleaner {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public DatabaseCleaner(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void clean() {
        transaction.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL session_replication_role = replica");
            // CASCADE czyści też wszystko, co od tych tabel zależy (studia, grupy, oceny, obecności)
            jdbc.execute("TRUNCATE audit_log, refresh_tokens, login_throttle, users, faculties, academic_terms, rooms CASCADE");
        });
    }
}