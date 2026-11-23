package pl.tokstudiow.api.studies;

import java.util.UUID;

/**
 * Wynik importu jednej osoby. Hasło startowe jest zwracane tylko w tej odpowiedzi,
 * żeby dziekanat mógł je przekazać studentowi; nigdzie nie jest zapisywane jawnym tekstem.
 */
public record ImportedStudentResponse(
        UUID studyId,
        UUID studentId,
        String firstName,
        String lastName,
        String email,
        String albumNumber,
        String initialPassword
) {
}
