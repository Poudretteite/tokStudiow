package pl.tokstudiow.api.grades;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PartialGradeRequest(
        @NotNull UUID enrollmentId,
        @NotNull UUID assessmentItemId,
        @NotNull UUID gradeValueId
) {
}