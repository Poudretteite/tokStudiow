package pl.tokstudiow.api.grades;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Test jednostkowy: kolejność terminów I termin → poprawka → komisja. */
class FinalAttemptTest {

    @Test
    void attemptsFollowEachOther() {
        assertThat(FinalAttempt.FIRST.next()).isEqualTo(FinalAttempt.SECOND);
        assertThat(FinalAttempt.SECOND.next()).isEqualTo(FinalAttempt.COMMISSION);
        assertThat(FinalAttempt.COMMISSION.next()).isNull();
    }

    @Test
    void previousIsInverseOfNext() {
        assertThat(FinalAttempt.FIRST.previous()).isNull();
        for (FinalAttempt attempt : FinalAttempt.values()) {
            if (attempt.next() != null) {
                assertThat(attempt.next().previous()).isEqualTo(attempt);
            }
        }
    }
}
