package pl.tokstudiow.api.university;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record RoomRequest(
        @NotBlank @Size(max = 50) String building,
        @NotBlank @Size(max = 20) String number,
        @Positive @Max(1000) @Nullable Short capacity
) {
}