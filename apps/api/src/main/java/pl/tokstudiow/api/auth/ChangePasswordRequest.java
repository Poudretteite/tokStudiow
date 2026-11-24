package pl.tokstudiow.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Górny limit chroni przed haszowaniem ogromnych danych; właściwe zasady sprawdza {@link PasswordPolicy}. */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @Size(max = 200) String newPassword
) {
}
