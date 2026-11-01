package pl.tokstudiow.api.groups;

import jakarta.persistence.*;
import pl.tokstudiow.api.studies.Study;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_enrollments")
public class GroupEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_group_id", nullable = false)
    private ClassGroup classGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    protected GroupEnrollment() {
        // wymagany przez JPA
    }

    public GroupEnrollment(ClassGroup classGroup, Study study) {
        this.classGroup = classGroup;
        this.study = study;
        this.enrolledAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ClassGroup getClassGroup() { return classGroup; }
    public Study getStudy() { return study; }
    public Instant getEnrolledAt() { return enrolledAt; }
}