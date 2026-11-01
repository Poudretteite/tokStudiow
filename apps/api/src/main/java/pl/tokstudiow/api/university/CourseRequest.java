package pl.tokstudiow.api.university;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CourseRequest(
        @NotNull UUID fieldOfStudyId,
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull @Min(0) @Max(60) Short ects,
        @NotNull @Min(1) @Max(12) Short semesterNumber,
        @NotEmpty @Size(max = 5) List<@Valid @NotNull CourseComponentRequest> components
) {
}