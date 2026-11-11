package pl.tokstudiow.api.grades;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BatchGradeEntry(@NotNull UUID enrollmentId, @NotNull UUID gradeValueId) {
}