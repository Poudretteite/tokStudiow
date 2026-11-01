package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AssessmentItemRepository extends JpaRepository<AssessmentItem, UUID> {

    List<AssessmentItem> findByClassGroupIdOrderByOrderIndex(UUID classGroupId);

    long countByClassGroupId(UUID classGroupId);

    @Query("select sum(a.weightPercent) from AssessmentItem a where a.classGroup.id = :classGroupId")
    @Nullable BigDecimal sumWeights(@Param("classGroupId") UUID classGroupId);
}