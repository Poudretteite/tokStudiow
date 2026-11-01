package pl.tokstudiow.api.university;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CourseComponentRequest(
        @NotNull ComponentType type,
        @NotNull @Min(1) @Max(500) Short hours,
        @NotNull UUID gradingScaleId
) {
}