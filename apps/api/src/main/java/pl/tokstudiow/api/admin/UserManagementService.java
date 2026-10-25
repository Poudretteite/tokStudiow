package pl.tokstudiow.api.admin;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.auth.RefreshTokenRepository;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserManagementService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;

    public UserManagementService(UserRepository users,
                                 PasswordEncoder passwordEncoder,
                                 RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public User create(CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already in use");
        }
        return users.save(new User(
                request.firstName().trim(),
                request.lastName().trim(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()));
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

        user.deactivate();
        refreshTokens.revokeAllActiveForUser(userId, Instant.now());
    }
}