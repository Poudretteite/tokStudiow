import type { ScheduleConflict, ScheduleEntry, WeekFrequency } from '../api/types';
import { COMPONENT_LABELS } from '../i18n/labels';

/**
 * Logika planu zajęć bez Reacta: tygodnie semestru, parzystość, rozkład bloków w siatce.
 * Tydzień 1 to tydzień (od poniedziałku), w którym zaczyna się semestr – tak samo liczy serwer.
 */

export const DAYS: { day: number; short: string; name: string }[] = [
    { day: 1, short: 'Pon', name: 'Poniedziałek' },
    { day: 2, short: 'Wt', name: 'Wtorek' },
    { day: 3, short: 'Śr', name: 'Środa' },
    { day: 4, short: 'Czw', name: 'Czwartek' },
    { day: 5, short: 'Pt', name: 'Piątek' },
    { day: 6, short: 'Sob', name: 'Sobota' },
    { day: 7, short: 'Nd', name: 'Niedziela' },
];

export const FREQUENCY_LABELS: Record<WeekFrequency, string> = {
    WEEKLY: 'co tydzień',
    ODD_WEEKS: 'tygodnie nieparzyste',
    EVEN_WEEKS: 'tygodnie parzyste',
};

const DAY_MS = 24 * 60 * 60 * 1000;

/** „08:30” lub „08:30:00” → minuty od północy. */
export function toMinutes(time: string): number {
    const [h, m] = time.split(':').map(Number);
    return h * 60 + m;
}

/** Minuty → „8:30”. */
export function formatMinutes(minutes: number): string {
    return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`;
}

export function timeRange(entry: Pick<ScheduleEntry, 'startTime' | 'endTime'>): string {
    return `${formatMinutes(toMinutes(entry.startTime))}–${formatMinutes(toMinutes(entry.endTime))}`;
}

/** Data „RRRR-MM-DD” jako liczba dni od 1970 r. (UTC – bez przesunięć przez strefę czasu). */
function epochDay(date: string): number {
    const [y, m, d] = date.split('-').map(Number);
    return Date.UTC(y, m - 1, d) / DAY_MS;
}

/** Poniedziałek tygodnia, w którym jest dany dzień (w dniach od 1970 r.). 1.01.1970 to czwartek. */
function mondayOf(day: number): number {
    return day - ((day + 3) % 7);
}

export function todayIso(now: Date = new Date()): string {
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
}

/** Numer tygodnia semestru dla danego dnia; przed początkiem semestru – 0 lub mniej. */
export function termWeek(termStart: string, date: string): number {
    return Math.floor((mondayOf(epochDay(date)) - mondayOf(epochDay(termStart))) / 7) + 1;
}

/** Data poniedziałku danego tygodnia semestru („RRRR-MM-DD”). */
export function weekMonday(termStart: string, week: number): string {
    const day = mondayOf(epochDay(termStart)) + (week - 1) * 7;
    return new Date(day * DAY_MS).toISOString().slice(0, 10);
}

export function appliesInWeek(frequency: WeekFrequency, week: number): boolean {
    return frequency === 'WEEKLY' || (frequency === 'ODD_WEEKS') === (week % 2 === 1);
}

/** Dni do pokazania: zawsze poniedziałek–piątek, weekend tylko gdy są w nim zajęcia. */
export function visibleDays(entries: ScheduleEntry[]): number[] {
    const days = [1, 2, 3, 4, 5];
    for (const weekend of [6, 7]) {
        if (entries.some((e) => e.dayOfWeek === weekend)) {
            days.push(weekend);
        }
    }
    return days;
}

/** Zakres godzin siatki: co najmniej 8–16, poszerzony do pełnych godzin wokół zajęć. */
export function hourRange(entries: ScheduleEntry[]): { from: number; to: number } {
    let from = 8 * 60;
    let to = 16 * 60;
    for (const e of entries) {
        from = Math.min(from, Math.floor(toMinutes(e.startTime) / 60) * 60);
        to = Math.max(to, Math.ceil(toMinutes(e.endTime) / 60) * 60);
    }
    return { from, to };
}

export interface PlacedEntry {
    entry: ScheduleEntry;
    lane: number;
    lanes: number;
}

/**
 * Rozmieszczenie zajęć jednego dnia w kolumnach – nakładające się bloki (np. tygodnie parzyste
 * i nieparzyste o tej samej porze) stają obok siebie zamiast jeden na drugim.
 */
export function placeDay(entries: ScheduleEntry[]): PlacedEntry[] {
    const sorted = [...entries].sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime));
    const placed: PlacedEntry[] = [];
    let cluster: PlacedEntry[] = [];
    let clusterEnd = -1;
    let laneEnds: number[] = [];

    const closeCluster = () => {
        const lanes = Math.max(1, laneEnds.length);
        cluster.forEach((p) => (p.lanes = lanes));
        cluster = [];
        laneEnds = [];
    };

    for (const entry of sorted) {
        const start = toMinutes(entry.startTime);
        const end = toMinutes(entry.endTime);
        if (start >= clusterEnd) {
            closeCluster();
        }
        let lane = laneEnds.findIndex((laneEnd) => laneEnd <= start);
        if (lane === -1) {
            lane = laneEnds.length;
            laneEnds.push(end);
        } else {
            laneEnds[lane] = end;
        }
        const item = { entry, lane, lanes: 1 };
        cluster.push(item);
        placed.push(item);
        clusterEnd = Math.max(clusterEnd, end);
    }
    closeCluster();
    return placed;
}

export function groupLabel(entry: Pick<ScheduleEntry, 'componentType' | 'groupNumber'>): string {
    return `${COMPONENT_LABELS[entry.componentType].toLowerCase()}, gr. ${entry.groupNumber}`;
}

/** Kolizja zgłoszona przez serwer jako zdanie dla dziekanatu. */
export function conflictText(conflict: ScheduleConflict): string {
    const e = conflict.entry;
    const day = DAYS.find((d) => d.day === e.dayOfWeek)?.name.toLowerCase() ?? '';
    const when = `${day} ${timeRange(e)}${e.frequency === 'WEEKLY' ? '' : `, ${FREQUENCY_LABELS[e.frequency]}`}`;
    const what = `${e.courseName} (${groupLabel(e)})`;
    switch (conflict.type) {
        case 'GROUP':
            return `Ta grupa ma już zajęcia w tym czasie: ${when}.`;
        case 'ROOM':
            return `Sala ${e.roomLabel} jest zajęta: ${what}, ${when}.`;
        case 'LECTURER':
            return `Prowadzący ma wtedy inne zajęcia: ${what}, ${when}.`;
        case 'STUDENTS':
            return `Studenci tej grupy (${conflict.studentCount ?? 0}) mają wtedy ${what}, ${when}.`;
    }
}
