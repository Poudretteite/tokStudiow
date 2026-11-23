package pl.tokstudiow.api.grades;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.tokstudiow.api.grades.GradeProposalCalculator.ItemResult;
import pl.tokstudiow.api.grades.GradeProposalCalculator.Proposal;
import pl.tokstudiow.api.grades.GradeProposalCalculator.ScaleValue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Testy jednostkowe propozycji oceny końcowej – bez bazy, sama arytmetyka i reguły. */
class GradeProposalCalculatorTest {

    // skala 2,0–5,0 jak w migracji V8, celowo nie po kolei – kalkulator sam ją sortuje
    private static final ScaleValue G50 = value("5,0", "5.0", true);
    private static final ScaleValue G20 = value("2,0", "2.0", false);
    private static final ScaleValue G30 = value("3,0", "3.0", true);
    private static final ScaleValue G35 = value("3,5", "3.5", true);
    private static final ScaleValue G40 = value("4,0", "4.0", true);
    private static final ScaleValue G45 = value("4,5", "4.5", true);
    private static final List<ScaleValue> NUMERIC = List.of(G50, G20, G30, G35, G40, G45);

    private static final ScaleValue ZAL = value("zal", null, true);
    private static final ScaleValue NZAL = value("nzal", null, false);
    private static final List<ScaleValue> PASS_FAIL = List.of(ZAL, NZAL);

    @Nested
    class WeightedAverage {

        @Test
        void averagesByWeightAndRoundsToNearestScaleValue() {
            // 0,4 × 3,0 + 0,6 × 5,0 = 4,20 → 4,0
            Proposal p = propose(graded("40", G30), graded("60", G50));

            assertThat(p.average()).isEqualByComparingTo("4.20");
            assertThat(p.value()).isEqualTo(G40);
        }

        @ParameterizedTest(name = "średnia {0} → {1}")
        @CsvSource(delimiter = '|', textBlock = """
                2.00 | 2,0
                2.49 | 2,0
                2.50 | 3,0
                3.24 | 3,0
                3.25 | 3,5
                3.74 | 3,5
                3.75 | 4,0
                4.24 | 4,0
                4.25 | 4,5
                4.74 | 4,5
                4.75 | 5,0
                5.00 | 5,0
                """)
        void nearestValueWithTiesGoingUp(String average, String expectedLabel) {
            // dokładnie w połowie między ocenami (2,50; 3,25; 3,75; 4,25; 4,75) wygrywa wyższa
            ScaleValue nearest = GradeProposalCalculator.nearest(new BigDecimal(average), sorted());

            assertThat(nearest.label()).isEqualTo(expectedLabel);
        }

        @Test
        void averageIsRoundedToTwoPlacesHalfUp() {
            // 1/3 × 3,0 + 1/3 × 3,5 + 1/3 × 3,5 przy wagach 33,33 / 33,33 / 33,34 = 3,33335 → 3,33
            BigDecimal average = GradeProposalCalculator.weightedAverage(List.of(
                    graded("33.33", G30), graded("33.33", G35), graded("33.34", G35)));

            assertThat(average).isEqualByComparingTo("3.33");
        }
    }

    @Nested
    class PassFailRequirements {

        @Test
        void failedRequirementGivesFailingGradeDespiteGoodAverage() {
            Proposal p = propose(graded("100", G50), passFail(NZAL));

            assertThat(p.average()).isEqualByComparingTo("5.00");
            assertThat(p.value()).isEqualTo(G20);
        }

        @Test
        void metRequirementLeavesAverageDecide() {
            assertThat(propose(graded("100", G45), passFail(ZAL)).value()).isEqualTo(G45);
        }

        @Test
        void passFailComponentPassesOnlyWhenEveryItemPassed() {
            List<ItemResult> passed = List.of(passFail(ZAL), passFail(ZAL));
            List<ItemResult> oneFailed = List.of(passFail(ZAL), passFail(NZAL));

            assertThat(GradeProposalCalculator.propose(true, PASS_FAIL, passed).value()).isEqualTo(ZAL);
            assertThat(GradeProposalCalculator.propose(true, PASS_FAIL, oneFailed).value()).isEqualTo(NZAL);
        }

        @Test
        void passFailComponentMayCountGradedItemsToo() {
            // wykład zaliczany na zal/nzal z kolokwium na ocenę: liczy się tylko zaliczenie elementów
            Proposal p = GradeProposalCalculator.propose(true, PASS_FAIL, List.of(graded("100", G30)));

            assertThat(p.value()).isEqualTo(ZAL);
            assertThat(p.average()).isEqualByComparingTo("3.00");
        }
    }

    @Nested
    class NoProposal {

        @Test
        void missingGradeInAnyItem() {
            assertThat(propose(graded("40", G50), graded("60", null))).isEqualTo(Proposal.NONE);
        }

        @Test
        void weightsNotSummingToHundred() {
            assertThat(propose(graded("40", G50), graded("50", G50))).isEqualTo(Proposal.NONE);
        }

        @Test
        void numericComponentWithoutAnyGradedItem() {
            assertThat(propose(passFail(ZAL))).isEqualTo(Proposal.NONE);
        }

        @Test
        void noItemsAtAll() {
            assertThat(GradeProposalCalculator.computable(false, List.of())).isFalse();
            assertThat(propose()).isEqualTo(Proposal.NONE);
        }
    }

    // pomocnicze

    private static Proposal propose(ItemResult... items) {
        return GradeProposalCalculator.propose(false, NUMERIC, List.of(items));
    }

    private static ItemResult graded(String weight, ScaleValue latest) {
        return new ItemResult(true, new BigDecimal(weight), latest);
    }

    private static ItemResult passFail(ScaleValue latest) {
        return new ItemResult(false, BigDecimal.ZERO, latest);
    }

    private static ScaleValue value(String label, String numeric, boolean passing) {
        return new ScaleValue(UUID.randomUUID(), label, numeric == null ? null : new BigDecimal(numeric), passing);
    }

    private static List<ScaleValue> sorted() {
        return List.of(G20, G30, G35, G40, G45, G50);
    }
}
