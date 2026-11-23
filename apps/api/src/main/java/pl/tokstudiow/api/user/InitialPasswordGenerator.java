package pl.tokstudiow.api.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Hasła startowe dla kont zakładanych hurtowo. Kryptograficzny generator, 16 znaków
 * bez łatwych do pomylenia przy przepisywaniu (0/O, 1/l/I). Hasło pokazuje się raz
 * osobie zakładającej konta; w bazie zostaje wyłącznie jego skrót BCrypt.
 */
@Component
public class InitialPasswordGenerator {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789-_!?";
    static final int LENGTH = 16;

    private final SecureRandom random;

    public InitialPasswordGenerator() {
        this(new SecureRandom());
    }

    InitialPasswordGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            // nextInt(bound) losuje równomiernie – bez przewagi początkowych znaków alfabetu
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
