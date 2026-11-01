package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssessmentItemResponse(
        UUID id,
        String name,
        BigDecimal weightPercent,
        short orderIndex,
        @Nullable LocalDate plannedDate
) {
    static AssessmentItemResponse from(AssessmentItem item) {
        return new AssessmentItemResponse(
                item.getId(), item.getName(), item.getWeightPercent(), item.getOrderIndex(), item.getPlannedDate());
    }
}