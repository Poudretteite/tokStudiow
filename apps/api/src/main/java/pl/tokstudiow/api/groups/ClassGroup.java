package pl.tokstudiow.api.groups;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.people.LecturerProfile;
import pl.tokstudiow.api.university.AcademicTerm;
import pl.tokstudiow.api.university.CourseComponent;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "class_groups")
public class ClassGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_component_id", nullable = false)
    private CourseComponent courseComponent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "academic_term_id", nullable = false)
    private AcademicTerm academicTerm;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private LecturerProfile lecturer;

    @Column(name = "group_number", nullable = false)
    private short groupNumber;

    @Column
    private @Nullable Short capacity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClassGroup() {
        // wymagany przez JPA
    }

    public ClassGroup(CourseComponent courseComponent, AcademicTerm academicTerm, LecturerProfile lecturer,
                      short groupNumber, @Nullable Short capacity) {
        this.courseComponent = courseComponent;
        this.academicTerm = academicTerm;
        this.lecturer = lecturer;
        this.groupNumber = groupNumber;
        this.capacity = capacity;
    }

    public UUID getId() { return id; }
    public CourseComponent getCourseComponent() { return courseComponent; }
    public AcademicTerm getAcademicTerm() { return academicTerm; }
    public LecturerProfile getLecturer() { return lecturer; }
    public short getGroupNumber() { return groupNumber; }
    public @Nullable Short getCapacity() { return capacity; }

    UUID facultyId() {
        return courseComponent.getCourse().getFieldOfStudy().getFaculty().getId();
    }

    UUID fieldOfStudyId() {
        return courseComponent.getCourse().getFieldOfStudy().getId();
    }
}