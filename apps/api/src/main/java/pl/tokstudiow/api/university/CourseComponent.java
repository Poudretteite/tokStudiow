package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_components")
public class CourseComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComponentType type;

    @Column(nullable = false)
    private short hours;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_scale_id", nullable = false)
    private GradingScale gradingScale;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourseComponent() {
        // wymagany przez JPA
    }

    CourseComponent(Course course, ComponentType type, short hours, GradingScale gradingScale) {
        this.course = course;
        this.type = type;
        this.hours = hours;
        this.gradingScale = gradingScale;
    }

    public UUID getId() { return id; }
    public Course getCourse() { return course; }
    public ComponentType getType() { return type; }
    public short getHours() { return hours; }
    public GradingScale getGradingScale() { return gradingScale; }
}