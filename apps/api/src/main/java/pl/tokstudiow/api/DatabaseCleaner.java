package pl.tokstudiow.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Czyści bazę między testami. Wyłącznie w testach: wyłącza triggery
 * (w tym ochronę audit_log) tylko na czas jednej transakcji.
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
            jdbc.execute("TRUNCATE audit_log, refresh_tokens, users CASCADE");
            jdbc.execute("TRUNCATE audit_log, refresh_tokens, users, faculties CASCADE");
        });
    }
}