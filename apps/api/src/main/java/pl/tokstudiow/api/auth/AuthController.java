package pl.tokstudiow.api.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";
    static final String CLIENT_HEADER = "X-Client";
    private static final String WEB_CLIENT = "web";

    private final AuthService authService;
    private final JwtProperties properties;

    public AuthController(AuthService authService, JwtProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               @RequestHeader(name = CLIENT_HEADER, required = false) @Nullable String client,
                               HttpServletResponse response) {
        return deliver(authService.login(request.email(), request.password()), client, response);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody(required = false) @Valid @Nullable RefreshRequest body,
                                 @CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String cookie,
                                 @RequestHeader(name = CLIENT_HEADER, required = false) @Nullable String client,
                                 HttpServletResponse response) {
        return deliver(authService.refresh(resolveRefreshToken(body, cookie, client)), client, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) @Valid @Nullable RefreshRequest body,
                       @CookieValue(name = REFRESH_COOKIE, required = false) @Nullable String cookie,
                       @RequestHeader(name = CLIENT_HEADER, required = false) @Nullable String client,
                       HttpServletResponse response) {
        String token = body != null ? body.refreshToken() : (isWeb(client) ? cookie : null);
        if (token != null) {
            authService.logout(token);
        }
        if (isWeb(client)) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        }
    }

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("id", jwt.getSubject(), "role", jwt.getClaimAsString("role"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    // pomocnicze

    /**
     * Przeglądarka (X-Client: web) dostaje refresh token w ciasteczku HttpOnly i nie widzi go w treści.
     * Inne klienty (aplikacja mobilna) dostają go w treści odpowiedzi.
     */
    private TokenResponse deliver(TokenResponse tokens, @Nullable String client, HttpServletResponse response) {
        if (!isWeb(client)) {
            return tokens;
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookie(tokens.refreshToken(), properties.refreshTokenTtl()).toString());
        return tokens.withoutRefreshToken();
    }

    /** Ciasteczko jest brane pod uwagę tylko z nagłówkiem X-Client – druga warstwa ochrony przed CSRF. */
    private static String resolveRefreshToken(@Nullable RefreshRequest body, @Nullable String cookie,
                                              @Nullable String client) {
        if (body != null) {
            return body.refreshToken();
        }
        if (isWeb(client) && cookie != null) {
            return cookie;
        }
        throw new InvalidRefreshTokenException();
    }

    private static boolean isWeb(@Nullable String client) {
        return WEB_CLIENT.equals(client);
    }

    private static ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}