package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fields_of_study")
public class FieldOfStudy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyMode mode;

    @Column(name = "semesters_count", nullable = false)
    private short semestersCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FieldOfStudy() {
        // wymagany przez JPA
    }

    public FieldOfStudy(Faculty faculty, String name, StudyLevel level, StudyMode mode, short semestersCount) {
        this.faculty = faculty;
        this.name = name;
        this.level = level;
        this.mode = mode;
        this.semestersCount = semestersCount;
    }

    public UUID getId() { return id; }
    public Faculty getFaculty() { return faculty; }
    public String getName() { return name; }
    public StudyLevel getLevel() { return level; }
    public StudyMode getMode() { return mode; }
    public short getSemestersCount() { return semestersCount; }
}