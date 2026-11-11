package pl.tokstudiow.api.grades;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.common.ConflictException;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.university.GradeValue;
import pl.tokstudiow.api.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "final_grades")
public class FinalGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_enrollment_id", nullable = false)
    private GroupEnrollment groupEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_value_id", nullable = false)
    private GradeValue gradeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinalAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinalGradeStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private @Nullable User approvedBy;

    @Column(name = "approved_at")
    private @Nullable Instant approvedAt;

    /** Optimistic locking: dwa równoczesne zapisy tej samej oceny nie nadpiszą się po cichu. */
    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinalGrade() {
        // wymagany przez JPA
    }

    public FinalGrade(GroupEnrollment groupEnrollment, FinalAttempt attempt, GradeValue gradeValue) {
        this.groupEnrollment = groupEnrollment;
        this.attempt = attempt;
        this.gradeValue = gradeValue;
        this.status = FinalGradeStatus.DRAFT;
    }

    void changeValue(GradeValue newValue) {
        requireDraft();
        this.gradeValue = newValue;
    }

    public void approve(User approver) {
        requireDraft();
        this.status = FinalGradeStatus.APPROVED;
        this.approvedBy = approver;
        this.approvedAt = Instant.now();
    }

    private void requireDraft() {
        if (status == FinalGradeStatus.APPROVED) {
            throw new ConflictException("Approved final grade cannot be changed");
        }
    }

    public boolean isApproved() { return status == FinalGradeStatus.APPROVED; }

    public UUID getId() { return id; }
    public GroupEnrollment getGroupEnrollment() { return groupEnrollment; }
    public GradeValue getGradeValue() { return gradeValue; }
    public FinalAttempt getAttempt() { return attempt; }
    public FinalGradeStatus getStatus() { return status; }
    public @Nullable User getApprovedBy() { return approvedBy; }
    public @Nullable Instant getApprovedAt() { return approvedAt; }
}