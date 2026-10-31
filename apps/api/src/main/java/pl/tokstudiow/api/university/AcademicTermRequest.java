package pl.tokstudiow.api.university;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AcademicTermRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull TermType type,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}