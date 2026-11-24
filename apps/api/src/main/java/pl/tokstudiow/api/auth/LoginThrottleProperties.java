package pl.tokstudiow.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Limity nieudanych prób logowania (app.login-throttle w application.yaml). */
@ConfigurationProperties(prefix = "app.login-throttle")
public record LoginThrottleProperties(
        int maxFailuresPerAccount,
        int maxFailuresPerIp,
        Duration window,
        Duration lockDuration
) {
    public LoginThrottleProperties {
        if (maxFailuresPerAccount < 2 || maxFailuresPerIp < 2) {
            throw new IllegalArgumentException("Login throttle limits must be at least 2");
        }
        if (window.isNegative() || window.isZero() || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Login throttle durations must be positive");
        }
    }
}
