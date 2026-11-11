package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Wiersz arkusza ocen końcowych prowadzącego.
 *
 * @param grades              wszystkie terminy studenta, od pierwszego
 * @param openAttempt         termin, którego dotyczy zatwierdzenie; null = przedmiot rozliczony
 * @param draftGradeValueId   wartość zapisanego, niezatwierdzonego szkicu (jeśli jest)
 */
public record FinalGradeSheetRowResponse(
        UUID enrollmentId,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String albumNumber,
        @Nullable BigDecimal weightedAverage,
        @Nullable UUID proposedGradeValueId,
        @Nullable String proposedGradeLabel,
        List<FinalGradeResponse> grades,
        @Nullable FinalAttempt openAttempt,
        @Nullable UUID draftGradeValueId
) {
}