package pl.tokstudiow.api.grades;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.GradingScaleType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param gradingType   ocena (NUMERIC) albo zal/nzal (PASS_FAIL); brak = jak forma zajęć
 * @param weightPercent waga w średniej – wymagana dla ocen, pomijana dla zal/nzal
 */
public record AssessmentItemRequest(
        @NotBlank @Size(max = 100) String name,
        @Nullable GradingScaleType gradingType,
        @DecimalMin("0") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) @Nullable BigDecimal weightPercent,
        @Nullable LocalDate plannedDate
) {
}