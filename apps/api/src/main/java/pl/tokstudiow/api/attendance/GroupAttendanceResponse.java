package pl.tokstudiow.api.attendance;

import java.util.UUID;

/** Jedna komórka tabeli obecności grupy: student × zajęcia. */
public record GroupAttendanceResponse(
        UUID sessionId,
        UUID enrollmentId,
        AttendanceStatus status
) {
    static GroupAttendanceResponse from(Attendance attendance) {
        return new GroupAttendanceResponse(
                attendance.getClassSession().getId(),
                attendance.getGroupEnrollment().getId(),
                attendance.getStatus());
    }
}