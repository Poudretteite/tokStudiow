package pl.tokstudiow.api.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.user.InitialPasswordGenerator;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zmiana hasła przez użytkownika i reset hasła przez administratora lub dziekanat.
 * Każda z tych operacji kończy wszystkie inne sesje konta (unieważnia refresh tokeny)
 * i trafia do dziennika audytu – bez hasła i jego skrótu.
 */
@Service
public class PasswordService {

    private static final String AUDIT_ENTITY = "user";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final InitialPasswordGenerator passwordGenerator;
    private final AuditService audit;
    private final JwtProperties properties;
    private final Clock clock;

    public PasswordService(UserRepository users,
                           PasswordEncoder passwordEncoder,
                           RefreshTokenRepository refreshTokens,
                           RefreshTokenService refreshTokenService,
                           TokenService tokenService,
                           InitialPasswordGenerator passwordGenerator,
                           AuditService audit,
                           JwtProperties properties,
                           Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
        this.passwordGenerator = passwordGenerator;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Użytkownik zmienia własne hasło, podając obecne. Pozostałe urządzenia zostają wylogowane,
     * a to, na którym zmieniono hasło, dostaje nowe tokeny – już bez ograniczenia „zmień hasło”.
     */
    @Transactional
    public TokenResponse changeOwn(UUID userId, ChangePasswordRequest request) {
        User user = users.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessRuleException("New password must differ from the current one");
        }
        String problem = PasswordPolicy.violation(request.newPassword(), user.getEmail(),
                user.getFirstName(), user.getLastName());
        if (problem != null) {
            throw new BusinessRuleException(problem);
        }

        Map<String, Object> before = snapshot(user);
        Instant now = clock.instant();
        user.changePassword(passwordEncoder.encode(request.newPassword()), now);
        refreshTokens.revokeAllActiveForUser(userId, now);
        audit.record(AUDIT_ENTITY, userId, AuditAction.UPDATE, before, withEvent(snapshot(user), "PASSWORD_CHANGED"));

        return new TokenResponse(
                tokenService.issueAccessToken(user),
                refreshTokenService.issue(user),
                "Bearer",
                properties.accessTokenTtl().toSeconds());
    }

    /**
     * Nowe losowe hasło startowe, które trzeba zmienić przy logowaniu. Wszystkie sesje konta
     * zostają zakończone. Uprawnienia (kto komu może zresetować) sprawdza wywołujący.
     */
    @Transactional
    public PasswordResetResponse reset(User user) {
        if (!user.isActive()) {
            throw new BusinessRuleException("Account is deactivated");
        }
        String password = passwordGenerator.generate();

        Map<String, Object> before = snapshot(user);
        user.setTemporaryPassword(passwordEncoder.encode(password));
        refreshTokens.revokeAllActiveForUser(user.getId(), clock.instant());
        audit.record(AUDIT_ENTITY, user.getId(), AuditAction.UPDATE, before, withEvent(snapshot(user), "PASSWORD_RESET"));

        return new PasswordResetResponse(user.getEmail(), password);
    }

    private static Map<String, Object> snapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("email", user.getEmail());
        snapshot.put("mustChangePassword", user.isMustChangePassword());
        return snapshot;
    }

    private static Map<String, Object> withEvent(Map<String, Object> snapshot, String event) {
        snapshot.put("event", event);
        return snapshot;
    }
}
