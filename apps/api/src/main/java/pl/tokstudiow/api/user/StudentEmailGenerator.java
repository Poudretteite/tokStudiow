package pl.tokstudiow.api.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Uczelniany adres studenta, tworzony automatycznie: pierwsza litera imienia, pierwsza
 * litera nazwiska i numer albumu, np. Anna Nowak, 123456 → an123456@tokstudiow.pl.
 * Numer albumu jest unikalny, więc adres też. Polskie litery zamieniane są na łacińskie (Ł → l).
 */
@Component
public class StudentEmailGenerator {

    private final String domain;

    public StudentEmailGenerator(@Value("${app.student-email-domain:tokstudiow.pl}") String domain) {
        this.domain = domain;
    }

    public String generate(String firstName, String lastName, String albumNumber) {
        return localPart(firstName, lastName, albumNumber) + "@" + domain;
    }

    /** Część przed @ – czysta funkcja, testowana jednostkowo. */
    static String localPart(String firstName, String lastName, String albumNumber) {
        String album = albumNumber.trim().toLowerCase(Locale.ROOT)
                .replace('/', '-')
                .replaceAll("[^a-z0-9-]", "");
        return initial(firstName) + initial(lastName) + album;
    }

    private static String initial(String name) {
        String ascii = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('ł', 'l')
                .replace('Ł', 'L')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z]", "");
        return ascii.isEmpty() ? "" : ascii.substring(0, 1);
    }
}
