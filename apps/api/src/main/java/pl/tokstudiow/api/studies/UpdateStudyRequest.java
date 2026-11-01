package pl.tokstudiow.api.studies;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateStudyRequest(
        @NotNull @Min(1) @Max(12) Short currentSemester,
        @NotNull StudyStatus status
) {
}