package pl.tokstudiow.api.university;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

public record GradeValueResponse(UUID id, String label, @Nullable BigDecimal numericValue, boolean passing) {

    static GradeValueResponse from(GradeValue value) {
        return new GradeValueResponse(value.getId(), value.getLabel(), value.getNumericValue(), value.isPassing());
    }
}