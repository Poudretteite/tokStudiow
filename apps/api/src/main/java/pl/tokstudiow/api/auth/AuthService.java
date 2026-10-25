package pl.tokstudiow.api.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository users,
                       TokenService tokenService,
                       RefreshTokenService refreshTokens,
                       JwtProperties properties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokenService = tokenService;
        this.refreshTokens = refreshTokens;
        this.properties = properties;
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
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