package pl.tokstudiow.api.university;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record GradingScaleResponse(UUID id, String name, GradingScaleType type, List<GradeValueResponse> values) {

    static GradingScaleResponse from(GradingScale scale) {
        return new GradingScaleResponse(
                scale.getId(),
                scale.getName(),
                scale.getType(),
                scale.getValues().stream()
                        .sorted(Comparator.comparingInt(GradeValue::getSortOrder))
                        .map(GradeValueResponse::from)
                        .toList());
    }
}