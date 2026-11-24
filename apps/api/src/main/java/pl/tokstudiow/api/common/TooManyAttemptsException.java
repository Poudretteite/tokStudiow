package pl.tokstudiow.api.common;

import java.time.Duration;

/** Za dużo nieudanych prób – kolejne są odrzucane bez sprawdzania hasła aż do końca blokady. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
