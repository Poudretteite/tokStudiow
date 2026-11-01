package pl.tokstudiow.api.grades;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FinalGradeRequest(
        @NotNull UUID enrollmentId,
        @NotNull FinalAttempt attempt,
        @NotNull UUID gradeValueId
) {
}