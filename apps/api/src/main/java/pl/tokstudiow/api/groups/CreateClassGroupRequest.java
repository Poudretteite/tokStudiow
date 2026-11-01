package pl.tokstudiow.api.groups;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record CreateClassGroupRequest(
        @NotNull UUID courseComponentId,
        @NotNull UUID academicTermId,
        @NotNull UUID lecturerId,
        @NotNull @Min(1) @Max(99) Short groupNumber,
        @Positive @Max(500) @Nullable Short capacity
) {
}