package pl.tokstudiow.api.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record MyAttendanceResponse(
        UUID sessionId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        AttendanceStatus status
) {
    static MyAttendanceResponse from(Attendance attendance) {
        var session = attendance.getClassSession();
        return new MyAttendanceResponse(
                session.getId(), session.getDate(), session.getStartTime(), session.getEndTime(),
                attendance.getStatus());
    }
}