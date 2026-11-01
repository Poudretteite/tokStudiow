package pl.tokstudiow.api.studies;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateStudyRequest(
        @NotNull UUID studentId,
        @NotNull UUID fieldOfStudyId,
        @NotNull @Min(2000) @Max(2100) Short startYear,
        @NotNull @Min(1) @Max(12) Short currentSemester
) {
}