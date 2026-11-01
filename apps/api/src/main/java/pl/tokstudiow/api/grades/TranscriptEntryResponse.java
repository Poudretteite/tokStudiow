package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.ComponentType;

import java.math.BigDecimal;
import java.time.Instant;

public record TranscriptEntryResponse(
        String courseCode,
        String courseName,
        ComponentType componentType,
        short ects,
        String academicTermName,
        FinalAttempt attempt,
        String gradeLabel,
        @Nullable BigDecimal numericValue,
        boolean passing,
        @Nullable Instant approvedAt
) {
}