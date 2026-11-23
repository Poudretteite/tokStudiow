import type { ClassGroup, FinalAttempt } from '../api/types';
import { formatDate } from '../i18n/format';

/**
 * Terminy korekt prowadzącego (backend podaje gotowe daty):
 * - oceny z I terminu i wpisane do końca sesji: koniec sesji + 2 dni,
 * - oceny z poprawki/komisji i wpisane później (sesja poprawkowa): koniec sesji poprawkowej + 2 dni.
 * Interfejs tylko ukrywa przyciski po terminie – o tym, czy korekta przejdzie, decyduje serwer.
 */

const UNIVERSITY_TIME_ZONE = 'Europe/Warsaw';

export type SessionDates = Pick<
    ClassGroup,
    'examSessionEnd' | 'correctionDeadline' | 'retakeSessionEnd' | 'retakeCorrectionDeadline'
>;

/** Data (RRRR-MM-DD) danej chwili w strefie uczelni – tak samo liczy ją serwer. */
export function todayAtUniversity(now: Date = new Date()): string {
    // en-CA formatuje daty jako RRRR-MM-DD
    return new Intl.DateTimeFormat('en-CA', { timeZone: UNIVERSITY_TIME_ZONE }).format(now);
}

/** Czy prowadzący może jeszcze sam poprawić ocenę; ostatni dzień terminu się liczy. */
export function lecturerMayCorrect(correctionDeadline: string, today: string = todayAtUniversity()): boolean {
    return today <= correctionDeadline;
}

/** Ocena cząstkowa wpisana po końcu sesji należy do sesji poprawkowej i ma jej termin. */
export function partialGradeDeadline(dates: SessionDates, issuedAt: string): string {
    return todayAtUniversity(new Date(issuedAt)) > dates.examSessionEnd
        ? dates.retakeCorrectionDeadline
        : dates.correctionDeadline;
}

/** Ocena końcowa: I termin – termin sesji, poprawka i komisja – termin sesji poprawkowej. */
export function finalGradeDeadline(dates: SessionDates, attempt: FinalAttempt): string {
    return attempt === 'FIRST' ? dates.correctionDeadline : dates.retakeCorrectionDeadline;
}

export function hasRetakeSession(dates: SessionDates): boolean {
    return dates.retakeSessionEnd !== dates.examSessionEnd;
}

/** Zdanie o sesjach i terminach korekt do nagłówka grupy. */
export function sessionSummary(dates: SessionDates, forLecturer: boolean): string {
    const who = forLecturer ? 'Pomyłki w ocenach poprawiasz sam' : 'Prowadzący poprawia pomyłki w ocenach';
    if (!hasRetakeSession(dates)) {
        return `Sesja kończy się ${formatDate(dates.examSessionEnd)}. ${who} do ${formatDate(dates.correctionDeadline)}, później tylko dziekanat.`;
    }
    return (
        `Sesja kończy się ${formatDate(dates.examSessionEnd)}, sesja poprawkowa ${formatDate(dates.retakeSessionEnd)}. ` +
        `${who}: z I terminu do ${formatDate(dates.correctionDeadline)}, z sesji poprawkowej do ` +
        `${formatDate(dates.retakeCorrectionDeadline)}; później tylko dziekanat.`
    );
}
