package pl.tokstudiow.api.auth;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Zasady hasła wybieranego przez użytkownika, wzorowane na NIST SP 800-63B: liczy się długość
 * i to, czy hasła nie da się łatwo odgadnąć, a nie wymuszanie cyfr i znaków specjalnych.
 * Czysta logika bez Springa – ma własne testy jednostkowe; frontend sprawdza to samo na bieżąco.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /** BCrypt bierze pod uwagę tylko pierwsze 72 bajty – dłuższe hasło dawałoby złudne poczucie siły. */
    public static final int MAX_BYTES = 72;
    static final int MIN_DISTINCT_CHARACTERS = 6;

    /** Fragmenty, od których zaczyna się każdy słownik ataku – plus nazwa naszego systemu. */
    static final List<String> COMMON_FRAGMENTS = List.of(
            "tokstudiow", "password", "haslo", "qwerty", "asdfgh", "zxcvbn", "123456", "654321",
            "abcdef", "1qaz2wsx", "zaq12wsx", "uniwersytet");

    private PasswordPolicy() {
    }

    /**
     * Pierwsze naruszenie zasad albo {@code null}, gdy hasło jest w porządku.
     * Komunikaty to klucze tłumaczeń frontendu (jak pozostałe błędy API).
     */
    public static @Nullable String violation(String password, String email, String firstName, String lastName) {
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
            return "Password must have at least 12 characters";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "Password is too long";
        }
        String normalized = normalize(password);
        String login = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        for (String personal : List.of(login, firstName, lastName)) {
            String part = normalize(personal);
            if (part.length() >= 3 && normalized.contains(part)) {
                return "Password must not contain your name or login";
            }
        }
        if (password.codePoints().distinct().count() < MIN_DISTINCT_CHARACTERS
                || COMMON_FRAGMENTS.stream().anyMatch(normalized::contains)) {
            return "Password is too easy to guess";
        }
        return null;
    }

    /** Małe litery bez polskich znaków i odstępów: „Hasło” i „HASLO” to dla słownika to samo. */
    static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('ł', 'l')
                .replaceAll("\\s", "");
    }
}
