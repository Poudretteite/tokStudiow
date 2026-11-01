package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_of_study_id", nullable = false)
    private FieldOfStudy fieldOfStudy;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private short ects;

    @Column(name = "semester_number", nullable = false)
    private short semesterNumber;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("type")
    private List<CourseComponent> components = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
        // wymagany przez JPA
    }

    public Course(FieldOfStudy fieldOfStudy, String code, String name, short ects, short semesterNumber) {
        this.fieldOfStudy = fieldOfStudy;
        this.code = code;
        this.name = name;
        this.ects = ects;
        this.semesterNumber = semesterNumber;
    }

    public void addComponent(ComponentType type, short hours, GradingScale gradingScale) {
        components.add(new CourseComponent(this, type, hours, gradingScale));
    }

    public UUID getId() { return id; }
    public FieldOfStudy getFieldOfStudy() { return fieldOfStudy; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public short getEcts() { return ects; }
    public short getSemesterNumber() { return semesterNumber; }
    public List<CourseComponent> getComponents() { return Collections.unmodifiableList(components); }
}