package pl.tokstudiow.api.admin;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.auth.RefreshTokenRepository;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserManagementService {

    private static final String AUDIT_ENTITY = "user";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;

    public UserManagementService(UserRepository users,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenRepository refreshTokens,
                                 AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.audit = audit;
    }

    @Transactional
    public User create(CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already in use");
        }
        User user = users.save(new User(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()));

        audit.record(AUDIT_ENTITY, user.getId(), AuditAction.CREATE, null, snapshot(user));
        return user;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return users.findAll(Sort.by("lastName", "firstName"));
    }

    @Transactional
    public void deactivate(UUID userId, UUID actingAdminId) {
        if (userId.equals(actingAdminId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Map<String, Object> before = snapshot(user);
        user.deactivate();
        refreshTokens.revokeAllActiveForUser(userId, Instant.now());

        audit.record(AUDIT_ENTITY, userId, AuditAction.UPDATE, before, snapshot(user));
    }

    /** Pola zapisywane w dzienniku – jawna lista, nigdy hash hasła. */
    private static Map<String, Object> snapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("firstName", user.getFirstName());
        snapshot.put("lastName", user.getLastName());
        snapshot.put("email", user.getEmail());
        snapshot.put("role", user.getRole().name());
        snapshot.put("active", user.isActive());
        return snapshot;
    }
}