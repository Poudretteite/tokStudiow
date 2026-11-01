package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

public record FinalGradeProposalResponse(
        UUID enrollmentId,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String albumNumber,
        @Nullable BigDecimal weightedAverage,
        @Nullable UUID proposedGradeValueId,
        @Nullable String proposedGradeLabel
) {
}