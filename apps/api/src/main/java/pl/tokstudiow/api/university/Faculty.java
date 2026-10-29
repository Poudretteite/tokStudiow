package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "faculties")
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "short_name", nullable = false, length = 20)
    private String shortName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Faculty() {
        // wymagany przez JPA
    }

    public Faculty(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }

    public void update(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getShortName() { return shortName; }
}