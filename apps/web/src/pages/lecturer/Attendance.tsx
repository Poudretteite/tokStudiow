import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { api } from '../../api/client.ts';
import type { AttendanceStatus, ClassGroup, ClassSession, GroupAttendance, RosterEntry } from '../../api/types.ts';
import { Panel } from '../../components/Panel.tsx';
import { QueryState } from '../../components/QueryState.tsx';
import { Alert } from '../../components/ui/Alert.tsx';
import { Button } from '../../components/ui/Button.tsx';
import { Table, Td, Th, Tr } from '../../components/ui/Table.tsx';
import { TextField } from '../../components/ui/TextField.tsx';
import { errorMessage } from '../../i18n/errors.ts';
import { formatDate, formatTime } from '../../i18n/format.ts';
import { ATTENDANCE_CLASSES, ATTENDANCE_LABELS, ATTENDANCE_SHORT } from '../../i18n/labels.ts';

const CYCLE: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'LATE', 'EXCUSED'];
const BY_KEY: Record<string, AttendanceStatus> = { o: 'PRESENT', n: 'ABSENT', s: 'LATE', u: 'EXCUSED' };
const SUMMARY_LABELS: Record<AttendanceStatus, string> = {
    PRESENT: 'obecni',
    ABSENT: 'nieobecni',
    LATE: 'spóźnieni',
    EXCUSED: 'usprawiedliwieni',
};

/** Dzisiejsza data w strefie użytkownika, w formacie RRRR-MM-DD. */
function today(): string {
    const now = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function Attendance({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const sessionsKey = ['lecturer', 'groups', groupId, 'sessions'];
    const recordsKey = ['lecturer', 'groups', groupId, 'attendance'];

    const roster = useQuery({
        queryKey: ['groups', groupId, 'students'],
        queryFn: () => api<RosterEntry[]>(`/api/groups/${groupId}/students`),
    });
    const sessions = useQuery({
        queryKey: sessionsKey,
        queryFn: () => api<ClassSession[]>(`/api/lecturer/groups/${groupId}/sessions`),
    });
    const records = useQuery({
        queryKey: recordsKey,
        queryFn: () => api<GroupAttendance[]>(`/api/lecturer/groups/${groupId}/attendance`),
    });

    return (
        <QueryState query={roster}>
            {(students) => (
                <QueryState query={sessions}>
                    {(sessionList) => (
                        <QueryState query={records}>
                            {(recordList) => (
                                <AttendanceSheet
                                    groupId={groupId}
                                    students={students}
                                    sessions={sessionList}
                                    records={recordList}
                                    sessionsKey={sessionsKey}
                                    recordsKey={recordsKey}
                                />
                            )}
                        </QueryState>
                    )}
                </QueryState>
            )}
        </QueryState>
    );
}

interface SheetProps {
    groupId: string;
    students: RosterEntry[];
    sessions: ClassSession[];
    records: GroupAttendance[];
    sessionsKey: string[];
    recordsKey: string[];
}

/**
 * Tabela „studenci × zajęcia”. Sprawdzanie obecności: jedno kliknięcie tworzy zajęcia,
 * wszyscy są domyślnie obecni, prowadzący zaznacza tylko wyjątki i zapisuje całą listę naraz.
 */
function AttendanceSheet({ groupId, students, sessions, records, sessionsKey, recordsKey }: SheetProps) {
    const queryClient = useQueryClient();
    const [editingId, setEditingId] = useState<string | null>(null);
    const [drafts, setDrafts] = useState<Record<string, AttendanceStatus>>({});
    const [notice, setNotice] = useState<string | null>(null);
    const cells = useRef<(HTMLButtonElement | null)[]>([]);
    const saveButton = useRef<HTMLButtonElement>(null);

    const statusOf = (sessionId: string, enrollmentId: string) =>
        records.find((r) => r.sessionId === sessionId && r.enrollmentId === enrollmentId)?.status;
    const editing = sessions.find((s) => s.id === editingId);
    const summary = CYCLE.filter((status) => status !== 'PRESENT')
        .map((status) => [status, Object.values(drafts).filter((d) => d === status).length] as const)
        .filter(([, count]) => count > 0)
        .map(([status, count]) => `${SUMMARY_LABELS[status]}: ${count}`)
        .join(', ');

    const save = useMutation({
        mutationFn: () =>
            api(`/api/lecturer/sessions/${encodeURIComponent(editingId!)}/attendance`, {
                method: 'PUT',
                body: JSON.stringify({
                    entries: students.map((s) => ({ enrollmentId: s.enrollmentId, status: drafts[s.enrollmentId] })),
                }),
            }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: recordsKey });
            setNotice(`Zapisano obecność z ${formatDate(editing!.date)}.`);
            setEditingId(null);
            setDrafts({});
        },
    });

    /** Otwiera listę: dotychczasowe wpisy, a pozostali domyślnie obecni. */
    function startEditing(sessionId: string) {
        const initial: Record<string, AttendanceStatus> = {};
        for (const s of students) {
            initial[s.enrollmentId] = statusOf(sessionId, s.enrollmentId) ?? 'PRESENT';
        }
        setDrafts(initial);
        setEditingId(sessionId);
        setNotice(null);
        save.reset();
        requestAnimationFrame(() => cells.current[0]?.focus());
    }

    function cancel() {
        setEditingId(null);
        setDrafts({});
        save.reset();
    }

    function setStatus(enrollmentId: string, status: AttendanceStatus) {
        setDrafts((d) => ({ ...d, [enrollmentId]: status }));
    }

    function cycle(enrollmentId: string) {
        const current = drafts[enrollmentId] ?? 'PRESENT';
        setStatus(enrollmentId, CYCLE[(CYCLE.indexOf(current) + 1) % CYCLE.length]);
    }

    // O / N / S / U – ustawia status i przechodzi do następnej osoby; ↑ ↓ – nawigacja
    function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number, enrollmentId: string) {
        const key = event.key.toLowerCase();
        // za ostatnią osobą kursor przechodzi na przycisk zapisu
        const focus = (i: number) => (i < students.length ? cells.current[i] : saveButton.current)?.focus();
        if (BY_KEY[key]) {
            event.preventDefault();
            setStatus(enrollmentId, BY_KEY[key]);
            focus(index + 1);
        } else if (event.key === 'ArrowDown') {
            event.preventDefault();
            focus(index + 1);
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            cells.current[index - 1]?.focus();
        }
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        save.mutate();
    }

    const isFuture = (session: ClassSession) => session.date > today();

    return (
        <div className="flex flex-col gap-6">
            <NewSessionForm
                groupId={groupId}
                disabled={editingId !== null}
                lastSession={sessions.at(-1)}
                onCreated={async (session) => {
                    await queryClient.invalidateQueries({ queryKey: sessionsKey });
                    if (isFuture(session)) {
                        setNotice(`Zaplanowano zajęcia na ${formatDate(session.date)}.`);
                    } else {
                        startEditing(session.id);
                    }
                }}
            />

            {students.length === 0 ? (
                <p className="text-muted">Do grupy nie zapisano jeszcze studentów.</p>
            ) : sessions.length === 0 ? (
                <p className="text-muted">Nie ma jeszcze zajęć. Dodaj pierwsze, aby sprawdzić obecność.</p>
            ) : (
                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    {notice && <Alert variant="success">{notice}</Alert>}

                    <Panel
                        title="Lista obecności"
                        flush
                        aside={
                            <span className="text-sm text-muted">
                                {CYCLE.map((s) => `${ATTENDANCE_SHORT[s]} – ${ATTENDANCE_LABELS[s].toLowerCase()}`).join(', ')}
                            </span>
                        }
                    >
                        <Table minWidth={260 + sessions.length * 76}>
                            <thead>
                            <tr>
                                <Th className="sticky left-0 z-10">Student</Th>
                                {sessions.map((session) => (
                                    <Th key={session.id} className="text-center">
                                        <div title={formatDate(session.date)}>{session.date.slice(8, 10)}.{session.date.slice(5, 7)}</div>
                                        <div className="text-xs font-normal text-muted">{formatTime(session.startTime)}</div>
                                        {session.id === editingId ? (
                                            <div className="mt-1 text-xs font-normal text-navy">edycja</div>
                                        ) : session.cancelled ? (
                                            <div className="mt-1 text-xs font-normal text-muted">odwołane</div>
                                        ) : isFuture(session) ? (
                                            <div className="mt-1 text-xs font-normal text-muted">zaplanowane</div>
                                        ) : (
                                            <button
                                                type="button"
                                                onClick={() => startEditing(session.id)}
                                                disabled={editingId !== null}
                                                aria-label={`Sprawdź obecność: ${formatDate(session.date)}`}
                                                className="mt-1 text-xs font-semibold text-navy underline underline-offset-2 disabled:cursor-not-allowed disabled:text-muted disabled:no-underline"
                                            >
                                                Edytuj
                                            </button>
                                        )}
                                    </Th>
                                ))}
                                <Th className="text-right">Obecność</Th>
                            </tr>
                            </thead>
                            <tbody>
                            {students.map((student, index) => {
                                const own = records.filter((r) => r.enrollmentId === student.enrollmentId);
                                const attended = own.filter((r) => r.status === 'PRESENT' || r.status === 'LATE').length;
                                return (
                                    <Tr key={student.enrollmentId}>
                                        <Td className="sticky left-0 z-10 bg-white">
                                            {student.lastName} {student.firstName}
                                            <div className="text-xs text-muted">{student.albumNumber}</div>
                                        </Td>
                                        {sessions.map((session) => {
                                            if (session.id === editingId) {
                                                const status = drafts[student.enrollmentId] ?? 'PRESENT';
                                                return (
                                                    <Td key={session.id} className="text-center">
                                                        <button
                                                            ref={(el) => {
                                                                cells.current[index] = el;
                                                            }}
                                                            type="button"
                                                            onClick={() => cycle(student.enrollmentId)}
                                                            onKeyDown={(e) => handleKeyDown(e, index, student.enrollmentId)}
                                                            aria-label={`${student.firstName} ${student.lastName}: ${ATTENDANCE_LABELS[status]}`}
                                                            title={ATTENDANCE_LABELS[status]}
                                                            className={`h-8 w-10 rounded-sm border font-semibold focus-visible:outline-2 focus-visible:outline-navy ${
                                                                status === 'PRESENT'
                                                                    ? 'border-line bg-white'
                                                                    : 'border-current bg-page'
                                                            } ${ATTENDANCE_CLASSES[status]}`}
                                                        >
                                                            {ATTENDANCE_SHORT[status]}
                                                        </button>
                                                    </Td>
                                                );
                                            }
                                            const status = statusOf(session.id, student.enrollmentId);
                                            return (
                                                <Td key={session.id} className="text-center">
                                                    {status ? (
                                                        <span
                                                            title={ATTENDANCE_LABELS[status]}
                                                            className={`font-semibold ${ATTENDANCE_CLASSES[status]}`}
                                                        >
                                                            {ATTENDANCE_SHORT[status]}
                                                        </span>
                                                    ) : (
                                                        <span className="text-muted">–</span>
                                                    )}
                                                </Td>
                                            );
                                        })}
                                        <Td className="text-right">
                                            {own.length === 0 ? (
                                                <span className="text-muted">–</span>
                                            ) : (
                                                `${Math.round((attended / own.length) * 100)}%`
                                            )}
                                        </Td>
                                    </Tr>
                                );
                            })}
                            </tbody>
                        </Table>

                        {editing && (
                            <div className="sticky bottom-0 z-20 flex flex-wrap items-center gap-3 border-t border-line bg-white px-4 py-3">
                                <Button ref={saveButton} type="submit" loading={save.isPending}>
                                    Zapisz obecność
                                </Button>
                                <Button type="button" variant="secondary" onClick={cancel}>
                                    Anuluj
                                </Button>
                                <span className="text-sm">{summary ? `Pozostali obecni; ${summary}.` : 'Wszyscy obecni.'}</span>
                                <p className="w-full text-sm text-muted">
                                    {formatDate(editing.date)}, {formatTime(editing.startTime)}–{formatTime(editing.endTime)}.
                                    Wszyscy są domyślnie obecni. Kliknij osobę, aby zmienić status (O → N → S → U), albo
                                    naciśnij O, N, S lub U – status się ustawi, a kursor przejdzie do następnej osoby.
                                </p>
                                {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
                            </div>
                        )}
                    </Panel>
                </form>
            )}
        </div>
    );
}

interface NewSessionFormProps {
    groupId: string;
    disabled: boolean;
    lastSession: ClassSession | undefined;
    onCreated: (session: ClassSession) => Promise<void>;
}

/** Data dzisiejsza i godziny z poprzednich zajęć – zwykle wystarczy jedno kliknięcie. */
function NewSessionForm({ groupId, disabled, lastSession, onCreated }: NewSessionFormProps) {
    const [date, setDate] = useState(today);
    const [startTime, setStartTime] = useState(lastSession ? formatTime(lastSession.startTime) : '08:00');
    const [endTime, setEndTime] = useState(lastSession ? formatTime(lastSession.endTime) : '09:30');

    const create = useMutation({
        mutationFn: () =>
            api<ClassSession>(`/api/lecturer/groups/${groupId}/sessions`, {
                method: 'POST',
                body: JSON.stringify({ date, startTime, endTime }),
            }),
        onSuccess: (session) => onCreated(session),
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        create.mutate();
    }

    return (
        <Panel title="Nowe zajęcia">
            <form onSubmit={handleSubmit} className="flex flex-col gap-3">
                <div className="grid items-end gap-4 sm:grid-cols-[1fr_auto_auto_auto]">
                    <TextField label="Data" type="date" required value={date} onChange={(e) => setDate(e.currentTarget.value)} />
                    <TextField
                        label="Od"
                        type="time"
                        required
                        value={startTime}
                        onChange={(e) => setStartTime(e.currentTarget.value)}
                    />
                    <TextField label="Do" type="time" required value={endTime} onChange={(e) => setEndTime(e.currentTarget.value)} />
                    <Button type="submit" loading={create.isPending} disabled={disabled}>
                        {date > today() ? 'Zaplanuj zajęcia' : 'Sprawdź obecność'}
                    </Button>
                </div>
                {create.isError && <Alert>{errorMessage(create.error)}</Alert>}
            </form>
        </Panel>
    );
}
