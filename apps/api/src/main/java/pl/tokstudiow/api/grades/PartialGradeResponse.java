package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartialGradeResponse(
        UUID id,
        UUID enrollmentId,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String albumNumber,
        UUID assessmentItemId,
        String assessmentItemName,
        String gradeLabel,
        @Nullable BigDecimal numericValue,
        boolean passing,
        short attempt,
        Instant issuedAt
) {
    static PartialGradeResponse from(PartialGrade grade) {
        var enrollment = grade.getGroupEnrollment();
        var student = enrollment.getStudy().getStudent();
        var value = grade.getGradeValue();
        return new PartialGradeResponse(
                grade.getId(),
                enrollment.getId(),
                student.getUserId(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                student.getAlbumNumber(),
                grade.getAssessmentItem().getId(),
                grade.getAssessmentItem().getName(),
                value.getLabel(),
                value.getNumericValue(),
                value.isPassing(),
                grade.getAttempt(),
                grade.getIssuedAt());
    }
}