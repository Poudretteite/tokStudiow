package pl.tokstudiow.api.attendance;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tokstudiow.api.audit.AuditAction;
import pl.tokstudiow.api.audit.AuditService;
import pl.tokstudiow.api.common.BusinessRuleException;
import pl.tokstudiow.api.common.CurrentUser;
import pl.tokstudiow.api.common.NotFoundException;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.groups.ClassGroupRepository;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.groups.GroupEnrollmentRepository;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.Room;
import pl.tokstudiow.api.university.RoomRepository;
import pl.tokstudiow.api.user.User;
import pl.tokstudiow.api.user.UserRepository;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private final ClassGroupRepository groups;
    private final GroupEnrollmentRepository enrollments;
    private final ClassSessionRepository sessions;
    private final AttendanceRepository attendance;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final AuditService audit;

    public AttendanceService(ClassGroupRepository groups,
                             GroupEnrollmentRepository enrollments,
                             ClassSessionRepository sessions,
                             AttendanceRepository attendance,
                             RoomRepository rooms,
                             UserRepository users,
                             AuditService audit) {
        this.groups = groups;
        this.enrollments = enrollments;
        this.sessions = sessions;
        this.attendance = attendance;
        this.rooms = rooms;
        this.users = users;
        this.audit = audit;
    }

    // zajęcia

    @Transactional
    public SessionResponse createSession(UUID groupId, CreateSessionRequest request) {
        ClassGroup group = myGroup(groupId);

        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessRuleException("End time must be after start time");
        }
        AcademicTerm term = group.getAcademicTerm();
        if (request.date().isBefore(term.getStartDate()) || request.date().isAfter(term.getEndDate())) {
            throw new BusinessRuleException("Session date must be within the academic term");
        }
        Room room = request.roomId() == null ? null : rooms.findById(request.roomId())
                .orElseThrow(() -> new NotFoundException("Room not found"));

        ClassSession session = sessions.save(new ClassSession(
                group, room, request.date(), request.startTime(), request.endTime()));

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("classGroupId", groupId.toString());
        snapshot.put("date", session.getDate().toString());
        snapshot.put("startTime", session.getStartTime().toString());
        snapshot.put("endTime", session.getEndTime().toString());
        audit.record("class_session", session.getId(), AuditAction.CREATE, null, snapshot);

        return SessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID groupId) {
        myGroup(groupId);
        return sessions.findByClassGroupIdOrderByDateAscStartTimeAsc(groupId).stream()
                .map(SessionResponse::from)
                .toList();
    }

    // obecności

    @Transactional
    public List<AttendanceResponse> record(UUID sessionId, RecordAttendanceRequest request) {
        ClassSession session = mySession(sessionId);
        if (session.isCancelled()) {
            throw new BusinessRuleException("Cannot record attendance for a cancelled session");
        }
        if (session.getDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Cannot record attendance before the session takes place");
        }

        Set<UUID> seen = new HashSet<>();
        for (AttendanceEntryRequest entry : request.entries()) {
            if (!seen.add(entry.enrollmentId())) {
                throw new BusinessRuleException("Each student may appear only once in the request");
            }
        }

        // Zapisy tylko z grupy tych zajęć – wszystko albo nic
        Map<UUID, GroupEnrollment> groupEnrollments = enrollments.findRoster(session.getClassGroup().getId()).stream()
                .collect(Collectors.toMap(GroupEnrollment::getId, Function.identity()));
        User recorder = users.getReferenceById(CurrentUser.id());

        for (AttendanceEntryRequest entry : request.entries()) {
            GroupEnrollment enrollment = groupEnrollments.get(entry.enrollmentId());
            if (enrollment == null) {
                throw new NotFoundException("Enrollment not found");
            }

            Optional<Attendance> existing =
                    attendance.findByClassSessionIdAndGroupEnrollmentId(sessionId, enrollment.getId());
            if (existing.isEmpty()) {
                Attendance created = attendance.save(new Attendance(session, enrollment, entry.status(), recorder));
                audit.record("attendance", created.getId(), AuditAction.CREATE, null, snapshot(created));
            } else if (existing.get().getStatus() != entry.status()) {
                Attendance record = existing.get();
                Map<String, Object> before = snapshot(record);
                record.changeStatus(entry.status(), recorder);
                audit.record("attendance", record.getId(), AuditAction.UPDATE, before, snapshot(record));
            }
        }

        return attendance.findForSession(sessionId).stream().map(AttendanceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> sessionAttendance(UUID sessionId) {
        mySession(sessionId);
        return attendance.findForSession(sessionId).stream().map(AttendanceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MyAttendanceResponse> myAttendance(UUID groupId) {
        UUID studentId = CurrentUser.id();
        if (!enrollments.existsByClassGroupIdAndStudyStudentUserId(groupId, studentId)) {
            throw new NotFoundException("Group not found");
        }
        return attendance.findForStudentInGroup(groupId, studentId).stream()
                .map(MyAttendanceResponse::from)
                .toList();
    }

    // pomocnicze

    private ClassGroup myGroup(UUID groupId) {
        return groups.findDetailed(groupId)
                .filter(g -> g.getLecturer().getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    private ClassSession mySession(UUID sessionId) {
        return sessions.findById(sessionId)
                .filter(s -> s.getClassGroup().getLecturer().getUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Session not found"));
    }

    private static Map<String, Object> snapshot(Attendance record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sessionId", record.getClassSession().getId().toString());
        snapshot.put("enrollmentId", record.getGroupEnrollment().getId().toString());
        snapshot.put("status", record.getStatus().name());
        snapshot.put("recordedBy", record.getRecordedBy().getId().toString());
        return snapshot;
    }
}