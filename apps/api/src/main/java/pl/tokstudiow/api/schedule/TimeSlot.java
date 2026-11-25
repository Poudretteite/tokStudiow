package pl.tokstudiow.api.schedule;

import java.time.LocalTime;

/**
 * Termin w tygodniowym planie: dzień (1 = poniedziałek … 7 = niedziela, jak ISO 8601),
 * godziny i częstotliwość. Czysta logika bez Springa – ma własne testy jednostkowe.
 */
public record TimeSlot(int dayOfWeek, LocalTime start, LocalTime end, WeekFrequency frequency) {

    /** Zajęcia muszą zmieścić się w godzinach pracy budynków. */
    public static final LocalTime EARLIEST_START = LocalTime.of(7, 0);
    public static final LocalTime LATEST_END = LocalTime.of(21, 0);
    static final int MINUTE_STEP = 5;

    /**
     * Kolizja: ten sam dzień, wspólne tygodnie i nakładające się godziny.
     * Zajęcia kończące się o 9:30 nie kolidują z zaczynającymi się o 9:30.
     */
    public boolean overlaps(TimeSlot other) {
        return dayOfWeek == other.dayOfWeek
                && frequency.sharesWeeksWith(other.frequency)
                && start.isBefore(other.end)
                && other.start.isBefore(end);
    }

    /** Pierwszy błąd terminu albo null, gdy termin jest poprawny. */
    public String problem() {
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
        return null;
    }
}
