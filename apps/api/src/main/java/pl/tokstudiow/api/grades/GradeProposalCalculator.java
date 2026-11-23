package pl.tokstudiow.api.grades;

import org.jspecify.annotations.Nullable;
import pl.tokstudiow.api.university.GradeValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Propozycja oceny końcowej z ostatnich prób każdego elementu zaliczenia.
 * Czysta logika bez bazy i Springa, dlatego jest testowana jednostkowo.
 *
 * <ul>
 *   <li>elementy „na ocenę” tworzą średnią ważoną; wagi muszą dawać razem 100%,</li>
 *   <li>średnia jest zaokrąglana do najbliższej oceny ze skali, przy remisie w górę,</li>
 *   <li>elementy zal/nzal są warunkiem: niezaliczony daje najniższą ocenę niezaliczającą,</li>
 *   <li>brak oceny z któregokolwiek elementu = brak propozycji.</li>
 * </ul>
 */
final class GradeProposalCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private GradeProposalCalculator() {
    }

    /** Wartość ze skali ocen – tyle, ile potrzeba do wyliczeń. */
    record ScaleValue(UUID id, String label, @Nullable BigDecimal numericValue, boolean passing) {

        static ScaleValue of(GradeValue value) {
            return new ScaleValue(value.getId(), value.getLabel(), value.getNumericValue(), value.isPassing());
        }
    }

    /**
     * Element zaliczenia z ostatnią oceną studenta.
     *
     * @param graded        element „na ocenę” (liczy się do średniej); false = zal/nzal
     * @param weightPercent waga w średniej, dla zal/nzal 0
     * @param latest        ostatnia próba studenta; null = brak oceny
     */
    record ItemResult(boolean graded, BigDecimal weightPercent, @Nullable ScaleValue latest) {
    }

    record Proposal(@Nullable BigDecimal average, @Nullable ScaleValue value) {
        static final Proposal NONE = new Proposal(null, null);
    }

    /**
     * Czy z tego zestawu elementów da się wyliczyć propozycję (niezależnie od ocen studentów):
     * są jakieś elementy, wagi elementów na ocenę dają 100%, a ocena liczbowa ma choć jeden taki element.
     */
    static boolean computable(boolean passFailComponent, List<ItemResult> items) {
        if (items.isEmpty()) {
            return false;
        }
        List<ItemResult> graded = items.stream().filter(ItemResult::graded).toList();
        BigDecimal weights = graded.stream().map(ItemResult::weightPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean weightsComplete = graded.isEmpty() || weights.compareTo(HUNDRED) == 0;
        return weightsComplete && (passFailComponent || !graded.isEmpty());
    }

    /**
     * @param passFailComponent forma zajęć zaliczana na zal/nzal
     * @param componentScale    skala oceny końcowej tej formy zajęć
     */
    static Proposal propose(boolean passFailComponent, List<ScaleValue> componentScale, List<ItemResult> items) {
        if (!computable(passFailComponent, items) || items.stream().anyMatch(i -> i.latest() == null)) {
            return Proposal.NONE;
        }
        List<ItemResult> graded = items.stream().filter(ItemResult::graded).toList();
        BigDecimal average = graded.isEmpty() ? null : weightedAverage(graded);

        if (passFailComponent) {
            boolean allPassed = items.stream().allMatch(i -> i.latest().passing());
            return new Proposal(average, componentScale.stream()
                    .filter(v -> v.passing() == allPassed)
                    .findFirst()
                    .orElseThrow());
        }

        List<ScaleValue> ascending = componentScale.stream()
                .filter(v -> v.numericValue() != null)
                .sorted(Comparator.comparing(ScaleValue::numericValue))
                .toList();
        boolean requirementsMet = items.stream()
                .filter(i -> !i.graded())
                .allMatch(i -> i.latest().passing());
        if (!requirementsMet) {
            return new Proposal(average, lowestFailing(ascending));
        }
        return new Proposal(average, nearest(average, ascending));
    }

    /** Średnia ważona, zaokrąglona do 2 miejsc (połowa w górę). */
    static BigDecimal weightedAverage(List<ItemResult> graded) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ItemResult item : graded) {
            sum = sum.add(item.weightPercent().multiply(item.latest().numericValue()));
        }
        return sum.divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Najbliższa ocena ze skali; przy remisie wyższa. */
    static ScaleValue nearest(BigDecimal average, List<ScaleValue> ascending) {
        ScaleValue best = ascending.getFirst();
        for (ScaleValue candidate : ascending) {
            BigDecimal distance = candidate.numericValue().subtract(average).abs();
            BigDecimal bestDistance = best.numericValue().subtract(average).abs();
            if (distance.compareTo(bestDistance) <= 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static ScaleValue lowestFailing(List<ScaleValue> ascending) {
        return ascending.stream().filter(v -> !v.passing()).findFirst().orElse(ascending.getFirst());
    }
}
