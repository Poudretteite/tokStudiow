package pl.tokstudiow.api.grades;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PartialGradeBatchRequest(
        @NotNull UUID assessmentItemId,
        @NotEmpty @Size(max = 500) List<@Valid @NotNull BatchGradeEntry> entries
) {
}