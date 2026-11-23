package pl.tokstudiow.api.auth;

/** Nowe hasło startowe – pokazywane jeden raz, nigdzie nie zapisywane jawnie. */
public record PasswordResetResponse(String email, String initialPassword) {
}
