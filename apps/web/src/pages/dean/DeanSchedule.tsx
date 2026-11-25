import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { ApiError, api } from '../../api/client';
import type { ClassGroup, Room, ScheduleEntry, WeekFrequency } from '../../api/types';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { TextField } from '../../components/ui/TextField';
import { errorMessage } from '../../i18n/errors';
import {
    DAYS,
    FREQUENCY_LABELS,
    conflictText,
    formatMinutes,
    frequencyText,
    termWeek,
    timeRange,
    toMinutes,
    weekLetter,
    weekMonday,
} from '../../lib/schedule';

/** Domyślna długość zajęć: dwie godziny lekcyjne. */
const DEFAULT_MINUTES = 90;

/**
 * Terminy grupy w tygodniowym planie. Serwer odrzuca termin, gdy sala, prowadzący
 * albo studenci grupy mają wtedy inne zajęcia – wszystkie kolizje widać od razu pod formularzem.
 */
export function DeanSchedule({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const scheduleKey = ['dean', 'groups', groupId, 'schedule'];
    const queryClient = useQueryClient();
    const schedule = useQuery({
        queryKey: scheduleKey,
        queryFn: () => api<ScheduleEntry[]>(`/api/dean-office/groups/${groupId}/schedule`),
    });
    const rooms = useQuery({ queryKey: ['rooms'], queryFn: () => api<Room[]>('/api/rooms') });
    const [notice, setNotice] = useState<string | null>(null);

    const remove = useMutation({
        mutationFn: (entry: ScheduleEntry) =>
            api(`/api/dean-office/schedule-entries/${encodeURIComponent(entry.id)}`, { method: 'DELETE' }),
        onSuccess: async (_, entry) => {
            await queryClient.invalidateQueries({ queryKey: scheduleKey });
            setNotice(`Usunięto termin: ${DAYS[entry.dayOfWeek - 1].name.toLowerCase()} ${timeRange(entry)}.`);
        },
    });

    return (
        <QueryState query={schedule}>
            {(entries) => (
                <div className="flex flex-col gap-4">
                    {notice && <Alert variant="success">{notice}</Alert>}
                    {remove.isError && <Alert>{errorMessage(remove.error)}</Alert>}
                    <Panel title="Terminy zajęć" flush>
                        {entries.length === 0 ? (
                            <p className="px-4 py-3 text-muted">Grupa nie ma jeszcze terminu w planie.</p>
                        ) : (
                            <Table minWidth={560}>
                                <thead>
                                <tr>
                                    <Th>Dzień</Th>
                                    <Th>Godziny</Th>
                                    <Th>Sala</Th>
                                    <Th>Częstotliwość</Th>
                                    <Th />
                                </tr>
                                </thead>
                                <tbody>
                                {entries.map((e) => (
                                    <Tr key={e.id}>
                                        <Td>{DAYS[e.dayOfWeek - 1].name}</Td>
                                        <Td className="tabular-nums">{timeRange(e)}</Td>
                                        <Td>{e.roomLabel}</Td>
                                        <Td>{frequencyText(e)}</Td>
                                        <Td className="text-right">
                                            <Button
                                                type="button"
                                                variant="secondary"
                                                className="px-3 py-1 text-sm"
                                                loading={remove.isPending && remove.variables?.id === e.id}
                                                aria-label={`Usuń termin: ${DAYS[e.dayOfWeek - 1].name} ${timeRange(e)}`}
                                                onClick={() => {
                                                    setNotice(null);
                                                    remove.mutate(e);
                                                }}
                                            >
                                                Usuń
                                            </Button>
                                        </Td>
                                    </Tr>
                                ))}
                                </tbody>
                            </Table>
                        )}
                    </Panel>
                    <AddEntryForm
                        group={group}
                        rooms={rooms.data ?? []}
                        scheduleKey={scheduleKey}
                        onAdded={(text) => setNotice(text)}
                    />
                </div>
            )}
        </QueryState>
    );
}

function AddEntryForm({
    group,
    rooms,
    scheduleKey,
    onAdded,
}: {
    group: ClassGroup;
    rooms: Room[];
    scheduleKey: string[];
    onAdded: (text: string) => void;
}) {
    const queryClient = useQueryClient();
    const [day, setDay] = useState('1');
    const [start, setStart] = useState('08:00');
    const [end, setEnd] = useState(formatTimeInput(toMinutes('08:00') + DEFAULT_MINUTES));
    const [endTouched, setEndTouched] = useState(false);
    const [frequency, setFrequency] = useState<WeekFrequency>('WEEKLY');
    const [roomId, setRoomId] = useState('');
    const [weeks, setWeeks] = useState<number[]>([]);
    const termWeeks = termWeek(group.termStartDate, group.termEndDate);
    const selectingWeeks = frequency === 'SELECTED_WEEKS';
    const sortedRooms = [...rooms].sort((a, b) => `${a.building} ${a.number}`.localeCompare(`${b.building} ${b.number}`, 'pl'));
    const needed = group.capacity;

    const add = useMutation({
        mutationFn: () =>
            api<ScheduleEntry>(`/api/dean-office/groups/${encodeURIComponent(group.id)}/schedule`, {
                method: 'POST',
                body: JSON.stringify({
                    dayOfWeek: Number(day),
                    startTime: start,
                    endTime: end,
                    frequency,
                    roomId,
                    weeks: selectingWeeks ? weeks : null,
                }),
            }),
        onSuccess: async (entry) => {
            await queryClient.invalidateQueries({ queryKey: scheduleKey });
            onAdded(`Dodano termin: ${DAYS[entry.dayOfWeek - 1].name.toLowerCase()} ${timeRange(entry)}, ${entry.roomLabel}.`);
        },
    });
    const conflicts = add.error instanceof ApiError ? add.error.conflicts : [];

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        onAdded('');
        add.mutate();
    }

    return (
        <Panel title="Dodaj termin">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
                    <Select label="Dzień" value={day} onChange={(e) => setDay(e.currentTarget.value)}>
                        {DAYS.map((d) => (
                            <option key={d.day} value={d.day}>
                                {d.name}
                            </option>
                        ))}
                    </Select>
                    <TextField
                        label="Od"
                        type="time"
                        required
                        min="07:00"
                        max="21:00"
                        step={300}
                        value={start}
                        onChange={(e) => {
                            const value = e.currentTarget.value;
                            setStart(value);
                            // koniec przesuwa się razem z początkiem, dopóki nikt go nie zmienił ręcznie
                            if (!endTouched && value) {
                                setEnd(formatTimeInput(Math.min(toMinutes(value) + DEFAULT_MINUTES, 21 * 60)));
                            }
                        }}
                    />
                    <TextField
                        label="Do"
                        type="time"
                        required
                        min="07:00"
                        max="21:00"
                        step={300}
                        value={end}
                        onChange={(e) => {
                            setEnd(e.currentTarget.value);
                            setEndTouched(true);
                        }}
                    />
                    <Select
                        label="Częstotliwość"
                        value={frequency}
                        onChange={(e) => setFrequency(e.currentTarget.value as WeekFrequency)}
                    >
                        {(Object.keys(FREQUENCY_LABELS) as WeekFrequency[]).map((f) => (
                            <option key={f} value={f}>
                                {FREQUENCY_LABELS[f]}
                            </option>
                        ))}
                    </Select>
                    <Select label="Sala" required value={roomId} onChange={(e) => setRoomId(e.currentTarget.value)}>
                        <option value="">wybierz</option>
                        {sortedRooms.map((r) => {
                            const tooSmall = needed !== null && r.capacity !== null && r.capacity < needed;
                            return (
                                <option key={r.id} value={r.id} disabled={tooSmall}>
                                    {r.building}, s. {r.number}
                                    {r.capacity !== null ? ` (${r.capacity} miejsc${tooSmall ? ' – za mała' : ''})` : ''}
                                </option>
                            );
                        })}
                    </Select>
                </div>
                {selectingWeeks && (
                    <WeekPicker
                        termStart={group.termStartDate}
                        termWeeks={termWeeks}
                        selected={weeks}
                        onChange={setWeeks}
                    />
                )}
                <p className="text-sm text-muted">
                    Tygodnie liczą się od początku semestru: tydzień 1 to A, tydzień 2 to B i tak dalej. Dwie grupy (np.
                    laboratoryjne) mogą dzielić salę, prowadzącego i godzinę, jeśli jedna ma zajęcia w tygodnie A, a druga w
                    B. Gdy plan zmienia się co tydzień (np. kierunki medyczne) albo trzeba pominąć przerwę, wybierz konkretne
                    tygodnie.
                </p>

                {add.isError && (
                    <Alert>
                        {errorMessage(add.error)}
                        {conflicts.length > 0 && (
                            <ul className="mt-1 list-disc pl-5">
                                {conflicts.map((c, i) => (
                                    <li key={i}>{conflictText(c)}</li>
                                ))}
                            </ul>
                        )}
                    </Alert>
                )}

                <div>
                    <Button type="submit" loading={add.isPending} disabled={!roomId || (selectingWeeks && weeks.length === 0)}>
                        Dodaj termin
                    </Button>
                </div>
            </form>
        </Panel>
    );
}

/**
 * Wybór tygodni semestru jednym kliknięciem na tydzień, ze skrótami: wszystkie A, wszystkie B,
 * cały semestr. Przy każdym tygodniu data poniedziałku, żeby łatwo pominąć przerwę świąteczną.
 */
function WeekPicker({
    termStart,
    termWeeks,
    selected,
    onChange,
}: {
    termStart: string;
    termWeeks: number;
    selected: number[];
    onChange: (weeks: number[]) => void;
}) {
    const all = Array.from({ length: termWeeks }, (_, i) => i + 1);
    const chosen = new Set(selected);
    const toggle = (week: number) =>
        onChange(chosen.has(week) ? selected.filter((w) => w !== week) : [...selected, week].sort((a, b) => a - b));
    const mondayLabel = (week: number) => {
        const [, month, dayOfMonth] = weekMonday(termStart, week).split('-');
        return `${Number(dayOfMonth)}.${month}`;
    };

    return (
        <fieldset>
            <legend className="mb-1 text-sm font-semibold">
                Tygodnie <span className="font-normal text-muted">(wybrano: {selected.length})</span>
            </legend>
            <div className="mb-2 flex flex-wrap gap-2 text-sm">
                {[
                    { label: 'Wszystkie A', weeks: all.filter((w) => w % 2 === 1) },
                    { label: 'Wszystkie B', weeks: all.filter((w) => w % 2 === 0) },
                    { label: 'Cały semestr', weeks: all },
                    { label: 'Wyczyść', weeks: [] },
                ].map((preset) => (
                    <Button
                        key={preset.label}
                        type="button"
                        variant="secondary"
                        className="px-3 py-1 text-sm"
                        onClick={() => onChange(preset.weeks)}
                    >
                        {preset.label}
                    </Button>
                ))}
            </div>
            <div className="grid grid-cols-4 gap-1.5 sm:grid-cols-8 lg:grid-cols-11">
                {all.map((week) => {
                    const on = chosen.has(week);
                    return (
                        <button
                            key={week}
                            type="button"
                            aria-pressed={on}
                            onClick={() => toggle(week)}
                            className={`rounded-sm border px-1 py-1 text-center text-sm leading-tight focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy ${
                                on ? 'border-navy bg-navy text-white' : 'border-line bg-white hover:border-navy'
                            }`}
                        >
                            <span className="block font-semibold">
                                {week} {weekLetter(week)}
                            </span>
                            <span className={`block text-xs ${on ? 'opacity-85' : 'text-muted'}`}>{mondayLabel(week)}</span>
                        </button>
                    );
                })}
            </div>
        </fieldset>
    );
}

/** Minuty → „08:30” dla pola typu time. */
function formatTimeInput(minutes: number): string {
    const value = formatMinutes(minutes);
    return value.length === 4 ? `0${value}` : value;
}
