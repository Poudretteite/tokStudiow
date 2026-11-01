package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

public enum FinalAttempt {
    FIRST,
    SECOND,
    COMMISSION;

    /** Termin, który musi być zatwierdzony i niezaliczony, zanim można wystawić ten. */
    @Nullable FinalAttempt previous() {
        return switch (this) {
            case FIRST -> null;
            case SECOND -> FIRST;
            case COMMISSION -> SECOND;
        };
    }
}