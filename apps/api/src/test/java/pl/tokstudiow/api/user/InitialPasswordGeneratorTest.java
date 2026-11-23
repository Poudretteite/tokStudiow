package pl.tokstudiow.api.user;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Test jednostkowy generatora haseł startowych. */
class InitialPasswordGeneratorTest {

    private final InitialPasswordGenerator generator = new InitialPasswordGenerator();

    @RepeatedTest(20)
    void passwordHasSixteenCharactersFromSafeAlphabet() {
        String password = generator.generate();

        assertThat(password).hasSize(16);
        assertThat(password.chars()).allMatch(c -> InitialPasswordGenerator.ALPHABET.indexOf(c) >= 0);
        assertThat(password).doesNotContain("0", "O", "1", "l", "I");
    }

    @Test
    void passwordsDoNotRepeat() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            passwords.add(generator.generate());
        }
        assertThat(passwords).hasSize(1000);
    }

    @Test
    void passwordMeetsMinimumLengthForAccounts() {
        // konta wymagają co najmniej 12 znaków (CreateUserRequest)
        assertThat(generator.generate().length()).isGreaterThanOrEqualTo(12);
    }
}
