package pl.tokstudiow.api.university;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test jednostkowy terminów korekt prowadzącego: koniec sesji + 2 dni dla I terminu
 * i ocen wpisanych do końca sesji, koniec sesji poprawkowej + 2 dni dla poprawek.
 */
class CorrectionWindowTest {

    private static final LocalDate SESSION_END = LocalDate.of(2027, 2, 15);
    private static final LocalDate RETAKE_END = LocalDate.of(2027, 3, 1);
    private final AcademicTerm term = new AcademicTerm("2026/2027 zimowy", TermType.WINTER,
            LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 20), SESSION_END, RETAKE_END);

    @Test
    void deadlinesAreTwoDaysAfterEachSession() {
        assertThat(term.correctionDeadline()).isEqualTo(LocalDate.of(2027, 2, 17));
        assertThat(term.retakeCorrectionDeadline()).isEqualTo(LocalDate.of(2027, 3, 3));
    }

    @Test
    void firstTermGradesCanBeCorrectedUntilSessionEndPlusTwoDays() {
        assertThat(term.allowsLecturerCorrection(LocalDate.of(2027, 1, 10), false)).isTrue();
        assertThat(term.allowsLecturerCorrection(SESSION_END, false)).isTrue();
        assertThat(term.allowsLecturerCorrection(LocalDate.of(2027, 2, 17), false)).isTrue();
        assertThat(term.allowsLecturerCorrection(LocalDate.of(2027, 2, 18), false)).isFalse();
    }

    @Test
    void retakeGradesCanBeCorrectedUntilRetakeSessionEndPlusTwoDays() {
        assertThat(term.allowsLecturerCorrection(LocalDate.of(2027, 2, 18), true)).isTrue();
        assertThat(term.allowsLecturerCorrection(LocalDate.of(2027, 3, 3), true)).isTrue();
        assertThat(term.allowsLecturerCorrection(LocalDate.of(2027, 3, 4), true)).isFalse();
    }

    @Test
    void gradesEnteredAfterSessionEndBelongToRetakeSession() {
        assertThat(term.isRetakePeriod(LocalDate.of(2027, 2, 14))).isFalse();
        assertThat(term.isRetakePeriod(SESSION_END)).isFalse();
        assertThat(term.isRetakePeriod(LocalDate.of(2027, 2, 16))).isTrue();
    }

    @Test
    void deadlineCrossesMonthAndLeapDay() {
        AcademicTerm leap = new AcademicTerm("2027/2028 zimowy", TermType.WINTER,
                LocalDate.of(2027, 10, 1), LocalDate.of(2028, 3, 1), LocalDate.of(2028, 2, 28));
        assertThat(leap.correctionDeadline()).isEqualTo(LocalDate.of(2028, 3, 1));
    }

    @Test
    void sessionsEndWithTermWhenNotGiven() {
        AcademicTerm simple = new AcademicTerm("2026/2027 letni", TermType.SUMMER,
                LocalDate.of(2027, 2, 21), LocalDate.of(2027, 9, 30));
        assertThat(simple.getExamSessionEnd()).isEqualTo(LocalDate.of(2027, 9, 30));
        assertThat(simple.getRetakeSessionEnd()).isEqualTo(LocalDate.of(2027, 9, 30));
    }

    @Test
    void retakeSessionDefaultsToExamSession() {
        AcademicTerm noRetake = new AcademicTerm("2026/2027 zimowy", TermType.WINTER,
                LocalDate.of(2026, 10, 1), LocalDate.of(2027, 2, 20), SESSION_END);
        assertThat(noRetake.retakeCorrectionDeadline()).isEqualTo(noRetake.correctionDeadline());
    }
}
