package pl.tokstudiow.api.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Termin w planie: dzień (1 = poniedziałek … 7 = niedziela, jak ISO 8601), godziny, częstotliwość
 * i – dla {@link WeekFrequency#SELECTED_WEEKS} – numery tygodni semestru (rosnąco, bez powtórzeń).
 * Czysta logika bez Springa – ma własne testy jednostkowe.
 */
public record TimeSlot(int dayOfWeek, LocalTime start, LocalTime end, WeekFrequency frequency, List<Integer> weeks) {

    /** Zajęcia muszą zmieścić się w godzinach pracy budynków. */
    public static final LocalTime EARLIEST_START = LocalTime.of(7, 0);
    public static final LocalTime LATEST_END = LocalTime.of(21, 0);
    static final int MINUTE_STEP = 5;

    public TimeSlot {
        weeks = weeks == null ? List.of() : weeks.stream().distinct().sorted().toList();
    }

    public TimeSlot(int dayOfWeek, LocalTime start, LocalTime end, WeekFrequency frequency) {
        this(dayOfWeek, start, end, frequency, List.of());
    }

    /** Liczba tygodni semestru: od tygodnia z pierwszym dniem do tygodnia z ostatnim. */
    public static int weeksInTerm(LocalDate termStart, LocalDate termEnd) {
        LocalDate first = termStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate last = termEnd.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return (int) ChronoUnit.WEEKS.between(first, last) + 1;
    }

    /**
     * Kolizja: ten sam dzień, co najmniej jeden wspólny tydzień i nakładające się godziny.
     * Zajęcia kończące się o 9:30 nie kolidują z zaczynającymi się o 9:30.
     */
    public boolean overlaps(TimeSlot other) {
        return dayOfWeek == other.dayOfWeek
                && start.isBefore(other.end)
                && other.start.isBefore(end)
                && sharesWeekWith(other);
    }

    private boolean sharesWeekWith(TimeSlot other) {
        if (frequency == WeekFrequency.SELECTED_WEEKS && other.frequency == WeekFrequency.SELECTED_WEEKS) {
            return weeks.stream().anyMatch(other.weeks::contains);
        }
        if (frequency == WeekFrequency.SELECTED_WEEKS) {
            return weeks.stream().anyMatch(other.frequency::includes);
        }
        if (other.frequency == WeekFrequency.SELECTED_WEEKS) {
            return other.weeks.stream().anyMatch(frequency::includes);
        }
        return frequency == WeekFrequency.WEEKLY || other.frequency == WeekFrequency.WEEKLY || frequency == other.frequency;
    }

    /** Pierwszy błąd terminu albo null, gdy termin jest poprawny w semestrze o tylu tygodniach. */
    public String problem(int termWeeks) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return "Day of week must be between 1 and 7";
        }
        if (!start.isBefore(end)) {
            return "Classes must end after they start";
        }
        if (start.isBefore(EARLIEST_START) || end.isAfter(LATEST_END)) {
            return "Classes must fit between 7:00 and 21:00";
        }
        if (start.getMinute() % MINUTE_STEP != 0 || end.getMinute() % MINUTE_STEP != 0
                || start.getSecond() != 0 || end.getSecond() != 0) {
            return "Times must be multiples of 5 minutes";
        }
        if (frequency == WeekFrequency.SELECTED_WEEKS) {
            if (weeks.isEmpty()) {
                return "Choose at least one week";
            }
            if (weeks.getFirst() < 1 || weeks.getLast() > termWeeks) {
                return "Weeks must be within the academic term";
            }
        } else if (!weeks.isEmpty()) {
            return "Weeks can be chosen only for selected weeks";
        }
        return null;
    }
}
