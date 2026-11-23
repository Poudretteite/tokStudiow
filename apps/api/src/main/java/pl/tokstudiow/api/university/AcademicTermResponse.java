package pl.tokstudiow.api.university;

import java.time.LocalDate;
import java.util.UUID;

/**
 * @param examSessionEnd     koniec sesji – ten sam dla wszystkich
 * @param correctionDeadline ostatni dzień, w którym prowadzący sam poprawia oceny z I terminu (sesja + 2 dni)
 * @param retakeSessionEnd   koniec sesji poprawkowej
 * @param retakeCorrectionDeadline ostatni dzień korekt ocen z sesji poprawkowej (jej koniec + 2 dni)
 */
public record AcademicTermResponse(
        UUID id,
        String name,
        TermType type,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate examSessionEnd,
        LocalDate correctionDeadline,
        LocalDate retakeSessionEnd,
        LocalDate retakeCorrectionDeadline,
        boolean current
) {
    static AcademicTermResponse from(AcademicTerm term) {
        return new AcademicTermResponse(
                term.getId(), term.getName(), term.getType(),
                term.getStartDate(), term.getEndDate(),
                term.getExamSessionEnd(), term.correctionDeadline(),
                term.getRetakeSessionEnd(), term.retakeCorrectionDeadline(),
                term.isCurrentTerm());
    }
}
