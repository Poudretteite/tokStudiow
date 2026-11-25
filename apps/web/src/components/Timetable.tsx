import type { CSSProperties } from 'react';
import type { ScheduleEntry } from '../api/types';
import { DAYS, FREQUENCY_LABELS, formatMinutes, groupLabel, hourRange, placeDay, timeRange, toMinutes, visibleDays } from '../lib/schedule';

/** Wysokość jednej minuty w siatce (px): 90 minut zajęć mieści czas, nazwę, salę, częstotliwość i prowadzącego. */
const MINUTE_PX = 1;

/**
 * Tygodniowy plan: na szerokim ekranie siatka dni × godzin, na telefonie lista dzień po dniu.
 * `dates` – daty dni pokazywanego tygodnia (w widoku „ten tydzień”), np. { 1: '23.11' }.
 */
export function Timetable({
    entries,
    showLecturer,
    dates,
}: {
    entries: ScheduleEntry[];
    showLecturer: boolean;
    dates?: Record<number, string>;
}) {
    const days = visibleDays(entries);
    const { from, to } = hourRange(entries);
    const hours: number[] = [];
    for (let m = from; m < to; m += 60) {
        hours.push(m);
    }
    const height = (to - from) * MINUTE_PX;

    return (
        <>
            {/* siatka – od średniej szerokości ekranu */}
            <div className="hidden md:block">
                <div className="grid" style={{ gridTemplateColumns: `3.5rem repeat(${days.length}, minmax(0, 1fr))` }}>
                    <div />
                    {days.map((day) => (
                        <div key={day} className="border-b border-line px-2 pb-2 text-sm font-semibold">
                            {DAYS[day - 1].name}
                            {dates?.[day] && <span className="ml-1 font-normal text-muted">{dates[day]}</span>}
                        </div>
                    ))}

                    <div className="relative" style={{ height }} aria-hidden="true">
                        {hours.map((m) => (
                            <div
                                key={m}
                                className="absolute right-2 -translate-y-2 text-xs text-muted"
                                style={{ top: (m - from) * MINUTE_PX }}
                            >
                                {formatMinutes(m)}
                            </div>
                        ))}
                    </div>
                    {days.map((day) => (
                        <div key={day} className="relative border-l border-line" style={{ height }}>
                            {hours.map((m) => (
                                <div
                                    key={m}
                                    className="absolute inset-x-0 border-t border-line/70"
                                    style={{ top: (m - from) * MINUTE_PX }}
                                    aria-hidden="true"
                                />
                            ))}
                            {placeDay(entries.filter((e) => e.dayOfWeek === day)).map(({ entry, lane, lanes }) => (
                                <EntryBlock
                                    key={entry.id}
                                    entry={entry}
                                    showLecturer={showLecturer}
                                    style={{
                                        top: (toMinutes(entry.startTime) - from) * MINUTE_PX,
                                        height: (toMinutes(entry.endTime) - toMinutes(entry.startTime)) * MINUTE_PX,
                                        left: `calc(${(lane / lanes) * 100}% + 2px)`,
                                        width: `calc(${100 / lanes}% - 4px)`,
                                    }}
                                />
                            ))}
                        </div>
                    ))}
                </div>
            </div>

            {/* lista – telefon */}
            <div className="flex flex-col gap-4 md:hidden">
                {days
                    .filter((day) => entries.some((e) => e.dayOfWeek === day))
                    .map((day) => (
                        <section key={day}>
                            <h3 className="mb-1 font-semibold">
                                {DAYS[day - 1].name}
                                {dates?.[day] && <span className="ml-1 font-normal text-muted">{dates[day]}</span>}
                            </h3>
                            <ul className="flex flex-col divide-y divide-line border border-line bg-white">
                                {entries
                                    .filter((e) => e.dayOfWeek === day)
                                    .sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime))
                                    .map((e) => (
                                        <li key={e.id} className="flex gap-3 px-3 py-2">
                                            <span className="w-24 shrink-0 font-semibold tabular-nums">{timeRange(e)}</span>
                                            <EntryDetails entry={e} showLecturer={showLecturer} />
                                        </li>
                                    ))}
                            </ul>
                        </section>
                    ))}
            </div>
        </>
    );
}

function EntryBlock({
    entry,
    showLecturer,
    style,
}: {
    entry: ScheduleEntry;
    showLecturer: boolean;
    style: CSSProperties;
}) {
    const lecture = entry.componentType === 'LECTURE';
    return (
        <div
            className={`absolute overflow-hidden rounded-sm border border-l-4 px-1.5 py-1 text-xs leading-snug ${
                lecture ? 'border-navy bg-navy text-white' : 'border-navy/40 border-l-navy bg-white'
            }`}
            style={style}
            title={`${entry.courseName}, ${groupLabel(entry)}, ${timeRange(entry)}, ${entry.roomLabel}`}
        >
            <div className="font-semibold tabular-nums">{timeRange(entry)}</div>
            <EntryDetails entry={entry} showLecturer={showLecturer} inverted={lecture} />
        </div>
    );
}

function EntryDetails({
    entry,
    showLecturer,
    inverted = false,
}: {
    entry: ScheduleEntry;
    showLecturer: boolean;
    inverted?: boolean;
}) {
    const muted = inverted ? 'opacity-85' : 'text-muted';
    return (
        <div className="min-w-0">
            <div className="font-semibold">{entry.courseName}</div>
            <div className={muted}>
                {groupLabel(entry)}, {entry.roomLabel}
            </div>
            {entry.frequency !== 'WEEKLY' && <div className={muted}>{FREQUENCY_LABELS[entry.frequency]}</div>}
            {showLecturer && <div className={muted}>{entry.lecturerName}</div>}
        </div>
    );
}
