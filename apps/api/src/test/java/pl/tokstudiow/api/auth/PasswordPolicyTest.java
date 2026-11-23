package pl.tokstudiow.api.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Test jednostkowy zasad hasła – bez Springa i bazy. */
class PasswordPolicyTest {

    private static String check(String password) {
        return PasswordPolicy.violation(password, "jk123456@tokstudiow.pl", "Jan", "Kowalski");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zielony-rower-nad-Wisłą", "Mój kot śpi na parapecie", "Gdzie-jest-Nemo?42"})
    void acceptsLongPasswordsHardToGuess(String password) {
        assertThat(check(password)).isNull();
    }

    @Test
    void requiresAtLeastTwelveCharactersCountingPolishLettersOnce() {
        assertThat(check("Kot-na-dach")).isEqualTo("Password must have at least 12 characters");
        assertThat(check("żółw-i-ślimak")).isNull();     // 13 znaków, choć więcej bajtów
    }

    @Test
    void rejectsPasswordLongerThanBcryptCanCheck() {
        assertThat(check("ą".repeat(37))).isEqualTo("Password is too long");     // 74 bajty
        assertThat(check("zielony-rower-".repeat(5) + "!?")).isNull();           // dokładnie 72 bajty
    }

    @ParameterizedTest
    @ValueSource(strings = {"jan-ma-rower-2026", "KOWALSKI-rower-7", "rower-JK123456-xy", "kowalski z wisły"})
    void rejectsNameOrLoginRegardlessOfCase(String password) {
        assertThat(check(password)).isEqualTo("Password must not contain your name or login");
    }

    @Test
    void comparesNamesWithoutPolishLetters() {
        assertThat(PasswordPolicy.violation("lucja-ma-rower!", "lz1@tokstudiow.pl", "Łucja", "Żak"))
                .isEqualTo("Password must not contain your name or login");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Tokstudiow-2026!", "MojeHasło-zimowe", "qwerty-rower-12", "aaaaaaaabbbbbbbb",
            "rower-123456-xyz", "Zaq12wsx-rower!"})
    void rejectsPasswordsFromAttackDictionaries(String password) {
        assertThat(check(password)).isEqualTo("Password is too easy to guess");
    }
}
