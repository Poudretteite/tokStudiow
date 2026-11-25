package pl.tokstudiow.api.schedule;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.AcademicTerm;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Plan na semestr. Daty semestru pozwalają policzyć numer tygodnia (parzysty / nieparzysty).
 * Bez bieżącego semestru {@code term} jest pusty, a lista – pusta.
 */
public record ScheduleResponse(@Nullable Term term, List<ScheduleEntryResponse> entries) {

    public record Term(UUID id, String name, LocalDate startDate, LocalDate endDate) {
        static Term from(AcademicTerm term) {
            return new Term(term.getId(), term.getName(), term.getStartDate(), term.getEndDate());
        }
    }
}
