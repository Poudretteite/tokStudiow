package pl.tokstudiow.api.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.user.Role;

import java.time.LocalDate;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Role role,
        @NotBlank @Size(min = 12, max = 72) String password,

        // STUDENT
        @Pattern(regexp = "^[0-9A-Za-z/-]{1,20}$") @Nullable String albumNumber,
        @Past @Nullable LocalDate birthdate,

        // LECTURER, DEAN_OFFICE
        @Nullable UUID facultyId,
        @Size(max = 50) @Nullable String academicTitle
) {
}