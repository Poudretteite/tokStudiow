import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { Schedule } from '../api/types';
import { PageHeader } from '../components/PageHeader';
import { Panel } from '../components/Panel';
import { QueryState } from '../components/QueryState';
import { Timetable } from '../components/Timetable';
import { Button } from '../components/ui/Button';
import { formatDate } from '../i18n/format';
import { appliesInWeek, termWeek, todayIso, weekLetter, weekMonday } from '../lib/schedule';

type View = 'week' | 'term';

/** Plan zalogowanego: student widzi też prowadzących, prowadzący – tylko swoje grupy. */
export function MySchedulePage() {
    const { user } = useAuth();
    return user?.role === 'LECTURER' ? (
        <SchedulePage endpoint="/api/lecturer/schedule" showLecturer={false} />
    ) : (
        <SchedulePage endpoint="/api/me/schedule" showLecturer />
    );
}

/**
 * Plan zajęć studenta lub prowadzącego na bieżący semestr. Domyślnie bieżący tydzień
 * (z zajęciami tygodni parzystych lub nieparzystych tylko wtedy, gdy się odbywają),
 * a jednym kliknięciem cały plan semestru.
 */
export function SchedulePage({ endpoint, showLecturer }: { endpoint: string; showLecturer: boolean }) {
    const schedule = useQuery({ queryKey: ['schedule', endpoint], queryFn: () => api<Schedule>(endpoint) });
    const [view, setView] = useState<View>('week');
    const [weekShift, setWeekShift] = useState(0);

    return (
        <>
            <PageHeader title="Plan zajęć" crumbs={[{ label: 'Plan zajęć' }]} />
            <QueryState query={schedule}>
                {({ term, entries }) => {
                    if (!term) {
                        return <p className="text-muted">Nie ma bieżącego semestru – plan pojawi się, gdy dziekanat go ustawi.</p>;
                    }
                    const lastWeek = termWeek(term.startDate, term.endDate);
                    const todayWeek = Math.min(Math.max(termWeek(term.startDate, todayIso()), 1), lastWeek);
                    const week = Math.min(Math.max(todayWeek + weekShift, 1), lastWeek);
                    const monday = weekMonday(term.startDate, week);
                    const dates: Record<number, string> = {};
                    for (let day = 1; day <= 7; day++) {
                        const d = new Date(`${monday}T12:00:00Z`);
                        d.setUTCDate(d.getUTCDate() + day - 1);
                        dates[day] = `${d.getUTCDate()}.${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
                    }
                    const shown = view === 'week' ? entries.filter((e) => appliesInWeek(e, week)) : entries;

                    return (
                        <Panel
                            title={
                                view === 'week'
                                    ? `Tydzień ${week} (${weekLetter(week)}), od ${formatDate(monday)}`
                                    : `Cały semestr: ${term.name}`
                            }
                            aside={
                                <div className="flex flex-wrap items-center gap-2">
                                    {view === 'week' && (
                                        <>
                                            <Button
                                                type="button"
                                                variant="secondary"
                                                className="px-3 py-1 text-sm"
                                                disabled={week <= 1}
                                                onClick={() => setWeekShift(week - 1 - todayWeek)}
                                                aria-label="Poprzedni tydzień"
                                            >
                                                ‹
                                            </Button>
                                            <Button
                                                type="button"
                                                variant="secondary"
                                                className="px-3 py-1 text-sm"
                                                disabled={week === todayWeek}
                                                onClick={() => setWeekShift(0)}
                                            >
                                                Dziś
                                            </Button>
                                            <Button
                                                type="button"
                                                variant="secondary"
                                                className="px-3 py-1 text-sm"
                                                disabled={week >= lastWeek}
                                                onClick={() => setWeekShift(week + 1 - todayWeek)}
                                                aria-label="Następny tydzień"
                                            >
                                                ›
                                            </Button>
                                        </>
                                    )}
                                    <Button
                                        type="button"
                                        variant="secondary"
                                        className="px-3 py-1 text-sm"
                                        onClick={() => setView(view === 'week' ? 'term' : 'week')}
                                    >
                                        {view === 'week' ? 'Cały semestr' : 'Widok tygodnia'}
                                    </Button>
                                </div>
                            }
                        >
                            {entries.length === 0 ? (
                                <p className="text-muted">Dziekanat nie ułożył jeszcze planu Twoich zajęć w tym semestrze.</p>
                            ) : shown.length === 0 ? (
                                <p className="text-muted">W tym tygodniu nie masz zajęć.</p>
                            ) : (
                                <Timetable
                                    entries={shown}
                                    showLecturer={showLecturer}
                                    dates={view === 'week' ? dates : undefined}
                                />
                            )}
                        </Panel>
                    );
                }}
            </QueryState>
        </>
    );
}
