package pl.tokstudiow.api.schedule;

/**
 * W które tygodnie semestru odbywają się zajęcia. Tygodnie liczone są od poniedziałku tygodnia,
 * w którym zaczyna się semestr: tydzień 1, 2, 3…
 * <ul>
 *   <li>{@code ODD_WEEKS} – tydzień A (1., 3., 5. …), {@code EVEN_WEEKS} – tydzień B (2., 4., 6. …),
 *       np. dwie grupy laboratoryjne na zmianę w tej samej sali;</li>
 *   <li>{@code SELECTED_WEEKS} – wybrane tygodnie, gdy plan zmienia się co tydzień (kierunki medyczne)
 *       albo trzeba pominąć przerwę.</li>
 * </ul>
 */
public enum WeekFrequency {
    WEEKLY,
    ODD_WEEKS,
    EVEN_WEEKS,
    SELECTED_WEEKS;

    /** Czy zajęcia odbywają się w danym tygodniu (dla wybranych tygodni decyduje lista). */
    boolean includes(int week) {
        return switch (this) {
            case WEEKLY -> true;
            case ODD_WEEKS -> week % 2 == 1;
            case EVEN_WEEKS -> week % 2 == 0;
            case SELECTED_WEEKS -> false;
        };
    }
}
