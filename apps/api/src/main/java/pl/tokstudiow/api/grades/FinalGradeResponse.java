package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinalGradeResponse(
        UUID id,
        UUID enrollmentId,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String albumNumber,
        FinalAttempt attempt,
        FinalGradeStatus status,
        String gradeLabel,
        @Nullable BigDecimal numericValue,
        boolean passing,
        @Nullable UUID approvedBy,
        @Nullable Instant approvedAt
) {
    static FinalGradeResponse from(FinalGrade grade) {
        var enrollment = grade.getGroupEnrollment();
        var student = enrollment.getStudy().getStudent();
        var value = grade.getGradeValue();
        return new FinalGradeResponse(
                grade.getId(),
                enrollment.getId(),
                student.getUserId(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                student.getAlbumNumber(),
                grade.getAttempt(),
                grade.getStatus(),
                value.getLabel(),
                value.getNumericValue(),
                value.isPassing(),
                grade.getApprovedBy() == null ? null : grade.getApprovedBy().getId(),
                grade.getApprovedAt());
    }
}