package pl.tokstudiow.api.schedule;

/**
 * Jak często odbywają się zajęcia. Tygodnie liczone są od początku semestru:
 * tydzień, w którym semestr się zaczyna, to tydzień 1 (nieparzysty).
 */
public enum WeekFrequency {
    WEEKLY,
    ODD_WEEKS,
    EVEN_WEEKS;

    /** Czy dwa terminy mogą wypaść w tym samym tygodniu (co tydzień nakłada się z każdym). */
    boolean sharesWeeksWith(WeekFrequency other) {
        return this == WEEKLY || other == WEEKLY || this == other;
    }
}
