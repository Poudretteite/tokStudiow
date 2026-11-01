package pl.tokstudiow.api.university;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "grade_values")
public class GradeValue {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_scale_id", nullable = false)
    private GradingScale gradingScale;

    @Column(nullable = false, length = 20)
    private String label;

    @Column(name = "numeric_value", precision = 3, scale = 1)
    private @Nullable BigDecimal numericValue;

    @Column(name = "is_passing", nullable = false)
    private boolean passing;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    protected GradeValue() {
        // wymagany przez JPA
    }

    public UUID getId() { return id; }
    public GradingScale getGradingScale() { return gradingScale; }
    public String getLabel() { return label; }
    public @Nullable BigDecimal getNumericValue() { return numericValue; }
    public boolean isPassing() { return passing; }
    public short getSortOrder() { return sortOrder; }
}