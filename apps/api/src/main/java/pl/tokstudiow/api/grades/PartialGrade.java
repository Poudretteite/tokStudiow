package pl.tokstudiow.api.grades;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.tokstudiow.api.groups.GroupEnrollment;
import pl.tokstudiow.api.university.GradeValue;
import pl.tokstudiow.api.user.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partial_grades")
public class PartialGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_enrollment_id", nullable = false)
    private GroupEnrollment groupEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_item_id", nullable = false)
    private AssessmentItem assessmentItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_value_id", nullable = false)
    private GradeValue gradeValue;

    @Column(nullable = false)
    private short attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false)
    private User issuedBy;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PartialGrade() {
        // wymagany przez JPA
    }

    public PartialGrade(GroupEnrollment groupEnrollment, AssessmentItem assessmentItem,
                        GradeValue gradeValue, short attempt, User issuedBy) {
        this.groupEnrollment = groupEnrollment;
        this.assessmentItem = assessmentItem;
        this.gradeValue = gradeValue;
        this.attempt = attempt;
        this.issuedBy = issuedBy;
        this.issuedAt = Instant.now();
    }

    void correct(GradeValue newValue) {
        this.gradeValue = newValue;
    }

    public UUID getId() { return id; }
    public GroupEnrollment getGroupEnrollment() { return groupEnrollment; }
    public AssessmentItem getAssessmentItem() { return assessmentItem; }
    public GradeValue getGradeValue() { return gradeValue; }
    public short getAttempt() { return attempt; }
    public User getIssuedBy() { return issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
}