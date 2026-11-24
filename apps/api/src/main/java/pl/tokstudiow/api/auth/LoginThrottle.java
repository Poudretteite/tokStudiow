package pl.tokstudiow.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.TooManyAttemptsException;
import pl.tokstudiow.api.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Liczniki nieudanych prób (logowanie, podanie obecnego hasła przy zmianie) trzymane w bazie,
 * więc działają po restarcie i przy kilku instancjach aplikacji.
 * <ul>
 *   <li>konto: {@code max-failures-per-account} prób w oknie – chroni jedno konto przed zgadywaniem,
 *       działa tak samo dla adresów, których nie ma w systemie (brak wycieku listy kont);</li>
 *   <li>adres IP: {@code max-failures-per-ip} prób – chroni przed sprawdzaniem jednego hasła na wielu kontach.</li>
 * </ul>
 * W czasie blokady hasło nie jest w ogóle sprawdzane, więc zgadywanie nic nie daje.
 */
@Service
public class LoginThrottle {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final JdbcClient jdbc;
    private final LoginThrottleProperties properties;
    private final UserRepository users;
    private final AuditService audit;
    private final Clock clock;

    public LoginThrottle(JdbcClient jdbc, LoginThrottleProperties properties, UserRepository users,
                         AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    // klucze liczników

    static String accountKey(String email) {
        return "account:" + email.trim().toLowerCase(Locale.ROOT);
    }

    static String ipKey(String ip) {
        return "ip:" + ip;
    }

    static String passwordChangeKey(UUID userId) {
        return "password-change:" + userId;
    }

    /** W bazie tylko skrót – adres e-mail i IP to dane osobowe, a do liczenia prób nie są potrzebne jawnie. */
    static String hash(String key) {
        return RefreshTokenService.hash(key);
    }

    // sprawdzanie i liczenie

    /** Rzuca {@link TooManyAttemptsException}, jeśli którykolwiek z kluczy jest teraz zablokowany. */
    @Transactional(readOnly = true)
    public void checkAllowed(String... keys) {
        Instant now = clock.instant();
        List<String> hashes = Arrays.stream(keys).map(LoginThrottle::hash).toList();
        jdbc.sql("""
                SELECT locked_until FROM login_throttle
                WHERE key_hash IN (:hashes) AND locked_until > :now
                ORDER BY locked_until DESC
                LIMIT 1
                """)
                .param("hashes", hashes)
                .param("now", at(now))
                .query(OffsetDateTime.class)
                .optional()
                .ifPresent(lockedUntil -> {
                    throw new TooManyAttemptsException(Duration.between(now, lockedUntil.toInstant()));
                });
    }

    /**
     * Nieudane logowanie: liczy próbę dla konta i dla adresu IP. Osobna transakcja, bo logowanie
     * kończy się wyjątkiem, a licznik musi zostać zapisany. Blokada istniejącego konta trafia do audytu.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginFailure(String email, String ip) {
        if (recordFailure(accountKey(email), properties.maxFailuresPerAccount())) {
            users.findByEmailIgnoreCase(email.trim()).ifPresent(user -> {
                log.warn("Login locked for user {} after {} failed attempts", user.getId(),
                        properties.maxFailuresPerAccount());
                audit.record("user", user.getId(), AuditAction.UPDATE, null, lockEvent("LOGIN_LOCKED"));
            });
        }
        if (recordFailure(ipKey(ip), properties.maxFailuresPerIp())) {
            log.warn("Login locked for IP {} after {} failed attempts", ip, properties.maxFailuresPerIp());
        }
    }

    /** Błędne obecne hasło przy zmianie hasła – ten sam limit co dla logowania na konto. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPasswordChangeFailure(UUID userId) {
        if (recordFailure(passwordChangeKey(userId), properties.maxFailuresPerAccount())) {
            log.warn("Password change locked for user {}", userId);
            audit.record("user", userId, AuditAction.UPDATE, null, lockEvent("PASSWORD_CHANGE_LOCKED"));
        }
    }

    /** Udane logowanie lub reset hasła zeruje licznik konta (licznika IP nie – inaczej łatwo go obejść). */
    @Transactional
    public void clear(String key) {
        jdbc.sql("DELETE FROM login_throttle WHERE key_hash = :hash").param("hash", hash(key)).update();
    }

    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void removeStale() {
        Instant now = clock.instant();
        jdbc.sql("""
                DELETE FROM login_throttle
                WHERE window_started_at < :dayAgo AND (locked_until IS NULL OR locked_until < :now)
                """)
                .param("dayAgo", at(now.minus(Duration.ofDays(1))))
                .param("now", at(now))
                .update();
    }

    /**
     * Jedno zapytanie zamiast odczytu i zapisu – równoległe próby nie zgubią żadnej z nich.
     * Po upływie okna licznik zaczyna od nowa. Zwraca true, jeśli ta próba założyła blokadę.
     */
    private boolean recordFailure(String key, int maxFailures) {
        Instant now = clock.instant();
        int failures = jdbc.sql("""
                INSERT INTO login_throttle AS t (key_hash, failures, window_started_at, locked_until)
                VALUES (:hash, 1, :now, NULL)
                ON CONFLICT (key_hash) DO UPDATE SET
                    failures = CASE WHEN t.window_started_at < :windowStart THEN 1 ELSE t.failures + 1 END,
                    window_started_at = CASE WHEN t.window_started_at < :windowStart THEN :now
                                             ELSE t.window_started_at END,
                    locked_until = CASE
                        WHEN (CASE WHEN t.window_started_at < :windowStart THEN 1 ELSE t.failures + 1 END) >= :max
                            THEN :lockedUntil
                        ELSE t.locked_until END
                RETURNING failures
                """)
                .param("hash", hash(key))
                .param("now", at(now))
                .param("windowStart", at(now.minus(properties.window())))
                .param("max", maxFailures)
                .param("lockedUntil", at(now.plus(properties.lockDuration())))
                .query(Integer.class)
                .single();
        return failures == maxFailures;
    }

    private Map<String, Object> lockEvent(String event) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("event", event);
        value.put("failedAttempts", properties.maxFailuresPerAccount());
        value.put("lockMinutes", properties.lockDuration().toMinutes());
        return value;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
