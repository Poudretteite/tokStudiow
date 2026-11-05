package pl.tokstudiow.api.attendance;

import jakarta.persistence.*;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_session_id", nullable = false)
    private ClassSession classSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_enrollment_id", nullable = false)
    private GroupEnrollment groupEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttendanceStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected Attendance() {
        // wymagany przez JPA
    }

    public Attendance(ClassSession classSession, GroupEnrollment groupEnrollment,
                      AttendanceStatus status, User recordedBy) {
        this.classSession = classSession;
        this.groupEnrollment = groupEnrollment;
        this.status = status;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    void changeStatus(AttendanceStatus newStatus, User recorder) {
        this.status = newStatus;
        this.recordedBy = recorder;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ClassSession getClassSession() { return classSession; }
    public GroupEnrollment getGroupEnrollment() { return groupEnrollment; }
    public AttendanceStatus getStatus() { return status; }
    public User getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
}