package pl.tokstudiow.api.attendance;

import java.time.Instant;
import java.util.UUID;

public record AttendanceResponse(
        UUID id,
        UUID enrollmentId,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String albumNumber,
        AttendanceStatus status,
        Instant recordedAt
) {
    static AttendanceResponse from(Attendance attendance) {
        var enrollment = attendance.getGroupEnrollment();
        var student = enrollment.getStudy().getStudent();
        return new AttendanceResponse(
                attendance.getId(),
                enrollment.getId(),
                student.getUserId(),
                student.getUser().getFirstName(),
                student.getUser().getLastName(),
                student.getAlbumNumber(),
                attendance.getStatus(),
                attendance.getRecordedAt());
    }
}