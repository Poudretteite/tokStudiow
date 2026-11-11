package pl.tokstudiow.api.grades;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Zatwierdzenie ocen końcowych wielu studentów naraz; termin wybiera system. */
public record FinalGradeBatchRequest(
        @NotEmpty @Size(max = 500) List<@Valid @NotNull BatchGradeEntry> entries
) {
}