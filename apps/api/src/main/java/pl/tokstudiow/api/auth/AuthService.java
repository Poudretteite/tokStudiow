package pl.tokstudiow.api.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository users;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokens;
    private final JwtProperties properties;
    private final LoginThrottle throttle;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository users,
                       TokenService tokenService,
                       RefreshTokenService refreshTokens,
                       JwtProperties properties,
                       LoginThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
        this.throttle = throttle;
    }

    /**
     * Logowanie z ochroną przed zgadywaniem haseł: zablokowane konto lub adres IP dostaje 429
     * bez sprawdzania hasła, a każda nieudana próba jest liczona.
     */
    @Transactional
    public TokenResponse login(String email, String password, String clientIp) {
        String accountKey = LoginThrottle.accountKey(email);
        throttle.checkAllowed(accountKey, LoginThrottle.ipKey(clientIp));

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException e) {
            throttle.recordLoginFailure(email, clientIp);
            throw e;
        }

        throttle.clear(accountKey);
        User user = users.findById(UUID.fromString(authentication.getName())).orElseThrow();
        return tokensFor(user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String refreshToken) {
        User user = refreshTokens.consume(refreshToken);
        return tokensFor(user);
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    private TokenResponse tokensFor(User user) {
        return new TokenResponse(
                tokenService.issueAccessToken(user),
                refreshTokens.issue(user),
                "Bearer",
                properties.accessTokenTtl().toSeconds());
    }
}