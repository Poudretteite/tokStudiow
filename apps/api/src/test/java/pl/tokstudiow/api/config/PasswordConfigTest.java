package pl.tokstudiow.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test jednostkowy: aplikacja haszuje hasła BCryptem z kosztem 12. Testy integracyjne używają
 * szybszego kosztu 4 (TestcontainersConfiguration), więc ten test pilnuje, żeby produkcja nie osłabła.
 */
class PasswordConfigTest {

    private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

    @Test
    void productionHashesWithBcryptCostTwelve() {
        String hash = encoder.encode("Correct-Horse-Battery-1");

        assertThat(hash).startsWith("$2a$12$");
        assertThat(encoder.matches("Correct-Horse-Battery-1", hash)).isTrue();
        assertThat(encoder.matches("correct-horse-battery-1", hash)).isFalse();
    }
}
