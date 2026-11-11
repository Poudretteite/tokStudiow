package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.GradingScaleType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AssessmentItemResponse(
        UUID id,
        String name,
        GradingScaleType gradingType,
        UUID gradingScaleId,
        BigDecimal weightPercent,
        short orderIndex,
        @Nullable LocalDate plannedDate
) {
    static AssessmentItemResponse from(AssessmentItem item) {
        return new AssessmentItemResponse(
                item.getId(),
                item.getName(),
                item.getGradingScale().getType(),
                item.getGradingScale().getId(),
                item.getWeightPercent(),
                item.getOrderIndex(),
                item.getPlannedDate());
    }
}