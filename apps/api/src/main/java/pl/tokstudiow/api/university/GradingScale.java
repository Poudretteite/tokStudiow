package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "grading_scales")
public class GradingScale {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GradingScaleType type;

    @OneToMany(mappedBy = "gradingScale")
    private List<GradeValue> values = new ArrayList<>();

    protected GradingScale() {
        // wymagany przez JPA
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public GradingScaleType getType() { return type; }
    public List<GradeValue> getValues() { return Collections.unmodifiableList(values); }
}