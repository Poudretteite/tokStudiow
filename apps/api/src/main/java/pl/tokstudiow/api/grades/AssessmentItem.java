package pl.tokstudiow.api.grades;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.groups.ClassGroup;
import pl.tokstudiow.api.university.GradingScale;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assessment_items")
public class AssessmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_group_id", nullable = false)
    private ClassGroup classGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_scale_id", nullable = false)
    private GradingScale gradingScale;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent;

    @Column(name = "order_index", nullable = false)
    private short orderIndex;

    @Column(name = "planned_date")
    private @Nullable LocalDate plannedDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AssessmentItem() {
        // wymagany przez JPA
    }

    public AssessmentItem(ClassGroup classGroup, GradingScale gradingScale, String name,
                          BigDecimal weightPercent, short orderIndex, @Nullable LocalDate plannedDate) {
        this.classGroup = classGroup;
        this.gradingScale = gradingScale;
        this.name = name;
        this.weightPercent = weightPercent;
        this.orderIndex = orderIndex;
        this.plannedDate = plannedDate;
    }

    public UUID getId() { return id; }
    public ClassGroup getClassGroup() { return classGroup; }
    public GradingScale getGradingScale() { return gradingScale; }
    public String getName() { return name; }
    public BigDecimal getWeightPercent() { return weightPercent; }
    public short getOrderIndex() { return orderIndex; }
    public @Nullable LocalDate getPlannedDate() { return plannedDate; }
}