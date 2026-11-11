package pl.tokstudiow.api.attendance;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/api/lecturer/groups/{groupId}/sessions")
    @PreAuthorize("hasRole('LECTURER')")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@PathVariable UUID groupId, @Valid @RequestBody CreateSessionRequest request) {
        return attendanceService.createSession(groupId, request);
    }

    @GetMapping("/api/lecturer/groups/{groupId}/sessions")
    @PreAuthorize("hasRole('LECTURER')")
    public List<SessionResponse> listSessions(@PathVariable UUID groupId) {
        return attendanceService.listSessions(groupId);
    }

    @PutMapping("/api/lecturer/sessions/{sessionId}/attendance")
    @PreAuthorize("hasRole('LECTURER')")
    public List<AttendanceResponse> record(@PathVariable UUID sessionId,
                                           @Valid @RequestBody RecordAttendanceRequest request) {
        return attendanceService.record(sessionId, request);
    }

    @GetMapping("/api/lecturer/sessions/{sessionId}/attendance")
    @PreAuthorize("hasRole('LECTURER')")
    public List<AttendanceResponse> sessionAttendance(@PathVariable UUID sessionId) {
        return attendanceService.sessionAttendance(sessionId);
    }

    @GetMapping("/api/lecturer/groups/{groupId}/attendance")
    @PreAuthorize("hasRole('LECTURER')")
    public List<GroupAttendanceResponse> groupAttendance(@PathVariable UUID groupId) {
        return attendanceService.groupAttendance(groupId);
    }

    @GetMapping("/api/me/groups/{groupId}/attendance")
    @PreAuthorize("hasRole('STUDENT')")
    public List<MyAttendanceResponse> myAttendance(@PathVariable UUID groupId) {
        return attendanceService.myAttendance(groupId);
    }
}