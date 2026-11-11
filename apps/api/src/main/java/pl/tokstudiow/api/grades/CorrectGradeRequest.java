package pl.tokstudiow.api.grades;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Korekta błędnie wpisanej oceny – zawsze z uzasadnieniem, które trafia do dziennika audytu. */
public record CorrectGradeRequest(
        @NotNull UUID gradeValueId,
        @NotBlank @Size(max = 500) String reason
) {
}