package pl.tokstudiow.api.schedule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Test jednostkowy: kiedy dwa terminy kolidują i jaki termin jest poprawny. */
class TimeSlotTest {

    private static TimeSlot slot(int day, String start, String end, WeekFrequency frequency) {
        return new TimeSlot(day, LocalTime.parse(start), LocalTime.parse(end), frequency);
    }

    @ParameterizedTest(name = "{0} {1}-{2} {3} vs {4} {5}-{6} {7} → {8}")
    @CsvSource(delimiter = '|', textBlock = """
            1 | 08:00 | 09:30 | WEEKLY     | 1 | 09:00 | 10:30 | WEEKLY     | true
            1 | 08:00 | 09:30 | WEEKLY     | 1 | 09:30 | 11:00 | WEEKLY     | false
            1 | 08:00 | 09:30 | WEEKLY     | 2 | 08:00 | 09:30 | WEEKLY     | false
            1 | 08:00 | 12:00 | WEEKLY     | 1 | 09:00 | 10:00 | WEEKLY     | true
            1 | 08:00 | 09:30 | ODD_WEEKS  | 1 | 08:00 | 09:30 | EVEN_WEEKS | false
            1 | 08:00 | 09:30 | ODD_WEEKS  | 1 | 08:00 | 09:30 | ODD_WEEKS  | true
            1 | 08:00 | 09:30 | EVEN_WEEKS | 1 | 08:00 | 09:30 | WEEKLY     | true
            """)
    void overlapNeedsSameDaySharedWeeksAndCommonTime(int d1, String s1, String e1, WeekFrequency f1,
                                                     int d2, String s2, String e2, WeekFrequency f2, boolean expected) {
        TimeSlot a = slot(d1, s1, e1, f1);
        TimeSlot b = slot(d2, s2, e2, f2);
        assertThat(a.overlaps(b)).isEqualTo(expected);
        assertThat(b.overlaps(a)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1}-{2} → {3}")
    @CsvSource(delimiter = '|', nullValues = "OK", textBlock = """
            1 | 08:00 | 09:30 | OK
            6 | 07:00 | 21:00 | OK
            0 | 08:00 | 09:30 | Day of week must be between 1 and 7
            8 | 08:00 | 09:30 | Day of week must be between 1 and 7
            1 | 09:30 | 09:30 | Classes must end after they start
            1 | 10:00 | 09:30 | Classes must end after they start
            1 | 06:30 | 08:00 | Classes must fit between 7:00 and 21:00
            1 | 20:00 | 21:15 | Classes must fit between 7:00 and 21:00
            1 | 08:07 | 09:30 | Times must be multiples of 5 minutes
            """)
    void validatesDayHoursAndFiveMinuteSteps(int day, String start, String end, String expected) {
        assertThat(slot(day, start, end, WeekFrequency.WEEKLY).problem(15)).isEqualTo(expected);
    }

    // wybrane tygodnie (np. kierunki medyczne z innym planem co tydzień)

    private static TimeSlot weeks(Integer... weeks) {
        return new TimeSlot(1, LocalTime.of(8, 0), LocalTime.of(9, 30), WeekFrequency.SELECTED_WEEKS, List.of(weeks));
    }

    @Test
    void selectedWeeksCollideOnlyWhenTheyShareAWeek() {
        assertThat(weeks(1, 2, 3).overlaps(weeks(4, 5))).isFalse();
        assertThat(weeks(1, 2, 3).overlaps(weeks(3, 8))).isTrue();
        assertThat(weeks(2, 4).overlaps(slot(1, "08:00", "09:30", WeekFrequency.ODD_WEEKS))).isFalse();
        assertThat(slot(1, "08:00", "09:30", WeekFrequency.EVEN_WEEKS).overlaps(weeks(2, 4))).isTrue();
        assertThat(weeks(7).overlaps(slot(1, "09:00", "10:00", WeekFrequency.WEEKLY))).isTrue();
    }

    @Test
    void selectedWeeksMustBeChosenAndFitTheTerm() {
        assertThat(weeks().problem(15)).isEqualTo("Choose at least one week");
        assertThat(weeks(0, 3).problem(15)).isEqualTo("Weeks must be within the academic term");
        assertThat(weeks(3, 16).problem(15)).isEqualTo("Weeks must be within the academic term");
        assertThat(weeks(15, 1, 1).problem(15)).isNull();
        assertThat(weeks(15, 1, 1).weeks()).containsExactly(1, 15);
        assertThat(new TimeSlot(1, LocalTime.of(8, 0), LocalTime.of(9, 30), WeekFrequency.ODD_WEEKS, List.of(1)).problem(15))
                .isEqualTo("Weeks can be chosen only for selected weeks");
    }

    @Test
    void termWeeksCountFromMondayOfTheFirstWeek() {
        // 1.10.2026 to czwartek, 28.02.2027 – niedziela
        assertThat(TimeSlot.weeksInTerm(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4))).isEqualTo(1);
        assertThat(TimeSlot.weeksInTerm(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5))).isEqualTo(2);
        assertThat(TimeSlot.weeksInTerm(LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 28))).isEqualTo(22);
    }
}
