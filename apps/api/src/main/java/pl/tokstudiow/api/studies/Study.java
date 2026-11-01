package pl.tokstudiow.api.studies;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pl.tokstudiow.api.people.StudentProfile;
import pl.tokstudiow.api.university.FieldOfStudy;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "studies")
public class Study {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_of_study_id", nullable = false)
    private FieldOfStudy fieldOfStudy;

    @Column(name = "start_year", nullable = false)
    private short startYear;

    @Column(name = "current_semester", nullable = false)
    private short currentSemester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Study() {
        // wymagany przez JPA
    }

    public Study(StudentProfile student, FieldOfStudy fieldOfStudy, short startYear, short currentSemester) {
        this.student = student;
        this.fieldOfStudy = fieldOfStudy;
        this.startYear = startYear;
        this.currentSemester = currentSemester;
        this.status = StudyStatus.ACTIVE;
    }

    void update(short currentSemester, StudyStatus status) {
        this.currentSemester = currentSemester;
        this.status = status;
    }

    public UUID getId() { return id; }
    public StudentProfile getStudent() { return student; }
    public FieldOfStudy getFieldOfStudy() { return fieldOfStudy; }
    public short getStartYear() { return startYear; }
    public short getCurrentSemester() { return currentSemester; }
    public StudyStatus getStatus() { return status; }

    UUID facultyId() {
        return fieldOfStudy.getFaculty().getId();
    }
}