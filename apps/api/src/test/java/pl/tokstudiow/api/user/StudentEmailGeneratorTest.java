package pl.tokstudiow.api.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Test jednostkowy: adres studenta = inicjały + numer albumu + domena uczelni. */
class StudentEmailGeneratorTest {

    @ParameterizedTest(name = "{0} {1}, {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Anna      | Nowak          | 123456  | an123456
            Łucja     | Żak            | 123457  | lz123457
            Ewa       | Ślusarczyk-Kot | 2001    | es2001
            Zofia     | Ćwik           | AB-12   | zcab-12
            Jan       | Kowalski       | 12/2026 | jk12-2026
            """)
    void initialsAndAlbumNumberWithoutPolishLetters(String first, String last, String album, String expected) {
        assertThat(StudentEmailGenerator.localPart(first, last, album)).isEqualTo(expected);
    }

    @Test
    void ignoresSurroundingSpacesAndAddsDomain() {
        assertThat(new StudentEmailGenerator("tokstudiow.pl").generate("  Anna ", " Nowak", " 123456 "))
                .isEqualTo("an123456@tokstudiow.pl");
    }
}
