package pl.tokstudiow.api.university;

import java.time.LocalDate;
import java.util.UUID;

public record AcademicTermResponse(
        UUID id,
        String name,
        TermType type,
        LocalDate startDate,
        LocalDate endDate,
        boolean current
) {
    static AcademicTermResponse from(AcademicTerm term) {
        return new AcademicTermResponse(
                term.getId(), term.getName(), term.getType(),
                term.getStartDate(), term.getEndDate(), term.isCurrentTerm());
    }
}