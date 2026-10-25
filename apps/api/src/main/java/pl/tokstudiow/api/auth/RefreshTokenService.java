package pl.tokstudiow.api.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.user.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository tokens, JwtProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokens.save(new RefreshToken(user, hash(rawToken), Instant.now().plus(properties.refreshTokenTtl())));
        return rawToken;
    }

    /**
     * Zużywa refresh token (rotacja) i zwraca jego właściciela.
     * Ponowne użycie już zużytego tokenu unieważnia wszystkie sesje użytkownika.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public User consume(String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = tokens.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = token.getUser();

        if (token.isRevoked()) {
            tokens.revokeAllActiveForUser(user.getId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(now) || !user.isActive()) {
            throw new InvalidRefreshTokenException();
        }

        token.revoke(now);
        return user;
    }

    @Transactional
    public void revoke(String rawToken) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> token.revoke(Instant.now()));
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}