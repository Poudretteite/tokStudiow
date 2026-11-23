package pl.tokstudiow.api.university;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

public record AcademicTermRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull TermType type,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        // koniec sesji egzaminacyjnej; brak = koniec semestru
        @Nullable LocalDate examSessionEnd,
        // koniec sesji poprawkowej (może wypaść po końcu semestru); brak = koniec sesji
        @Nullable LocalDate retakeSessionEnd
) {
}
