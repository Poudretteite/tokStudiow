package pl.tokstudiow.api.university;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FacultyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20) String shortName
) {
}