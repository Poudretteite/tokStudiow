package pl.tokstudiow.api.university;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FieldOfStudyRequest(
        @NotNull UUID facultyId,
        @NotBlank @Size(max = 200) String name,
        @NotNull StudyLevel level,
        @NotNull StudyMode mode,
        @NotNull @Min(1) @Max(12) Short semestersCount
) {
}