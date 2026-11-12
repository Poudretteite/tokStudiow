import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useParams } from 'react-router';
import { api } from '../../api/client';
import type { ClassGroup, Course, FinalAttempt, FinalGrade, GradingScale, RosterEntry, StudyRecord } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { Tabs } from '../../components/ui/Tabs';
import { TextField } from '../../components/ui/TextField';
import { errorMessage } from '../../i18n/errors';
import { formatInstant } from '../../i18n/format';
import { gradeHint, parseGrade } from '../../i18n/grades';
import { ATTEMPT_LABELS, COMPONENT_LABELS } from '../../i18n/labels';

const ATTEMPT_ORDER: FinalAttempt[] = ['FIRST', 'SECOND', 'COMMISSION'];

export function DeanGroupPage() {
    const groupId = encodeURIComponent(useParams().groupId ?? '');
    const group = useQuery({
        queryKey: ['dean', 'group', groupId],
        queryFn: () => api<ClassGroup>(`/api/dean-office/groups/${groupId}`),
    });
    const title = group.data
        ? `${group.data.courseName}, ${COMPONENT_LABELS[group.data.componentType].toLowerCase()}, grupa ${group.data.groupNumber}`
        : 'Grupa';

    return (
        <>
            <PageHeader title={title} crumbs={[{ label: 'Grupy zajęciowe', to: '/dean/groups' }, { label: title }]} />
            <QueryState query={group}>
                {(g) => (
                    <>
                        <p className="-mt-3 mb-6 text-muted">
                            {g.courseCode}, {g.academicTermName}, prowadzący: {g.lecturerFirstName} {g.lecturerLastName}
                        </p>
                        <Tabs
                            label="Obsługa grupy"
                            tabs={[
                                { id: 'students', label: 'Studenci', content: <Students group={g} /> },
                                { id: 'protocol', label: 'Protokół ocen', content: <Protocol group={g} /> },
                            ]}
                        />
                    </>
                )}
            </QueryState>
        </>
    );
}

// ---------- studenci: lista i zapisy ----------

function Students({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const roster = useQuery({
        queryKey: ['groups', groupId, 'students'],
        queryFn: () => api<RosterEntry[]>(`/api/groups/${groupId}/students`),
    });
    const course = useQuery({
        queryKey: ['courses', group.courseId],
        queryFn: () => api<Course>(`/api/courses/${encodeURIComponent(group.courseId)}`),
    });
    const studies = useQuery({ queryKey: ['dean', 'studies'], queryFn: () => api<StudyRecord[]>('/api/dean-office/studies') });

    return (
        <QueryState query={roster}>
            {(students) => (
                <div className="flex flex-col gap-6">
                    <Panel
                        title="Zapisani studenci"
                        flush
                        aside={
                            <span className="text-sm">
                                {students.length}
                                {group.capacity !== null && ` z ${group.capacity} miejsc`}
                            </span>
                        }
                    >
                        {students.length === 0 ? (
                            <p className="p-4 text-muted">Nikt nie jest jeszcze zapisany.</p>
                        ) : (
                            <Table>
                                <thead>
                                <tr>
                                    <Th>Student</Th>
                                    <Th>Numer albumu</Th>
                                    <Th>Zapisano</Th>
                                </tr>
                                </thead>
                                <tbody>
                                {students.map((s) => (
                                    <Tr key={s.enrollmentId}>
                                        <Td>
                                            {s.lastName} {s.firstName}
                                        </Td>
                                        <Td>{s.albumNumber}</Td>
                                        <Td>{formatInstant(s.enrolledAt)}</Td>
                                    </Tr>
                                ))}
                                </tbody>
                            </Table>
                        )}
                    </Panel>

                    {course.data && studies.data && (
                        <EnrollPanel group={group} course={course.data} studies={studies.data} roster={students} />
                    )}
                </div>
            )}
        </QueryState>
    );
}

/**
 * Zapis wielu osób naraz: lista od razu zawężona do aktywnych studentów kierunku
 * z właściwego semestru, „zaznacz wszystkich” i jeden przycisk zapisu.
 */
function EnrollPanel({
    group,
    course,
    studies,
    roster,
}: {
    group: ClassGroup;
    course: Course;
    studies: StudyRecord[];
    roster: RosterEntry[];
}) {
    const queryClient = useQueryClient();
    const [search, setSearch] = useState('');
    const [onlySemester, setOnlySemester] = useState(true);
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [notice, setNotice] = useState<string | null>(null);

    const enrolled = new Set(roster.map((r) => r.studyId));
    const phrase = search.trim().toLowerCase();
    const candidates = studies.filter(
        (s) =>
            s.fieldOfStudyId === course.fieldOfStudyId &&
            s.status === 'ACTIVE' &&
            !enrolled.has(s.id) &&
            (!onlySemester || s.currentSemester === course.semesterNumber) &&
            (!phrase ||
                `${s.studentLastName} ${s.studentFirstName} ${s.albumNumber}`.toLowerCase().includes(phrase)),
    );
    const free = group.capacity === null ? null : group.capacity - roster.length;
    const allVisibleSelected = candidates.length > 0 && candidates.every((s) => selected.has(s.id));

    function toggle(id: string) {
        setSelected((current) => {
            const next = new Set(current);
            if (next.has(id)) {
                next.delete(id);
            } else {
                next.add(id);
            }
            return next;
        });
    }

    function toggleAllVisible() {
        setSelected((current) => {
            const next = new Set(current);
            for (const s of candidates) {
                if (allVisibleSelected) {
                    next.delete(s.id);
                } else {
                    next.add(s.id);
                }
            }
            return next;
        });
    }

    const enroll = useMutation({
        mutationFn: () =>
            api(`/api/dean-office/groups/${encodeURIComponent(group.id)}/enrollments/batch`, {
                method: 'POST',
                body: JSON.stringify({ studyIds: [...selected] }),
            }),
        onSuccess: async () => {
            const count = selected.size;
            await queryClient.invalidateQueries({ queryKey: ['groups', encodeURIComponent(group.id), 'students'] });
            await queryClient.invalidateQueries({ queryKey: ['dean', 'groups'] });
            setSelected(new Set());
            setNotice(`Zapisano do grupy: ${count}.`);
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setNotice(null);
        enroll.mutate();
    }

    const tooMany = free !== null && selected.size > free;

    return (
        <form onSubmit={handleSubmit}>
            <Panel title="Zapisz studentów" flush aside={free !== null && <span className="text-sm">Wolne miejsca: {free}</span>}>
                <div className="flex flex-wrap items-end gap-4 border-b border-line p-4">
                    <div className="min-w-60 flex-1">
                        <TextField
                            label="Szukaj"
                            placeholder="nazwisko, imię lub numer albumu"
                            value={search}
                            onChange={(e) => setSearch(e.currentTarget.value)}
                        />
                    </div>
                    <label className="flex items-center gap-2 pb-2 text-sm">
                        <input
                            type="checkbox"
                            checked={onlySemester}
                            onChange={(e) => setOnlySemester(e.currentTarget.checked)}
                            className="accent-navy"
                        />
                        Tylko semestr {course.semesterNumber} (semestr przedmiotu)
                    </label>
                </div>

                {candidates.length === 0 ? (
                    <p className="p-4 text-muted">
                        Brak studentów do zapisania. Lista obejmuje aktywnych studentów kierunku przedmiotu, którzy nie są
                        jeszcze w tej grupie.
                    </p>
                ) : (
                    <Table>
                        <thead>
                        <tr>
                            <Th className="w-10">
                                <input
                                    type="checkbox"
                                    aria-label="Zaznacz wszystkich widocznych"
                                    checked={allVisibleSelected}
                                    onChange={toggleAllVisible}
                                    className="accent-navy"
                                />
                            </Th>
                            <Th>Student</Th>
                            <Th>Numer albumu</Th>
                            <Th>Semestr</Th>
                        </tr>
                        </thead>
                        <tbody>
                        {candidates.map((s) => (
                            <Tr key={s.id}>
                                <Td>
                                    <input
                                        type="checkbox"
                                        id={`study-${s.id}`}
                                        checked={selected.has(s.id)}
                                        onChange={() => toggle(s.id)}
                                        className="accent-navy"
                                    />
                                </Td>
                                <Td>
                                    <label htmlFor={`study-${s.id}`} className="cursor-pointer">
                                        {s.studentLastName} {s.studentFirstName}
                                    </label>
                                </Td>
                                <Td>{s.albumNumber}</Td>
                                <Td>{s.currentSemester}</Td>
                            </Tr>
                        ))}
                        </tbody>
                    </Table>
                )}

                <div className="flex flex-wrap items-center gap-3 border-t border-line px-4 py-3">
                    <Button type="submit" loading={enroll.isPending} disabled={selected.size === 0 || tooMany}>
                        Zapisz wybranych ({selected.size})
                    </Button>
                    {tooMany && <span className="text-sm text-red-700">Wybrano więcej osób niż wolnych miejsc.</span>}
                    {notice && <span className="text-sm text-green-700">{notice}</span>}
                </div>
                {enroll.isError && (
                    <div className="px-4 pb-4">
                        <Alert>{errorMessage(enroll.error)}</Alert>
                    </div>
                )}
            </Panel>
        </form>
    );
}

// ---------- protokół ocen końcowych i korekta ----------

function Protocol({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const grades = useQuery({
        queryKey: ['dean', 'groups', groupId, 'final-grades'],
        queryFn: () => api<FinalGrade[]>(`/api/dean-office/groups/${groupId}/final-grades`),
    });
    const scales = useQuery({ queryKey: ['grading-scales'], queryFn: () => api<GradingScale[]>('/api/grading-scales') });
    const scale = scales.data?.find((s) => s.id === group.gradingScaleId);
    const [correcting, setCorrecting] = useState<FinalGrade | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    return (
        <QueryState query={grades}>
            {(list) => {
                // Korygować można tylko ostatni termin studenta
                const latest = new Map<string, FinalGrade>();
                for (const g of list) {
                    const current = latest.get(g.enrollmentId);
                    if (!current || ATTEMPT_ORDER.indexOf(g.attempt) > ATTEMPT_ORDER.indexOf(current.attempt)) {
                        latest.set(g.enrollmentId, g);
                    }
                }
                return (
                    <div className="flex flex-col gap-4">
                        {notice && <Alert variant="success">{notice}</Alert>}
                        <Panel title="Oceny końcowe" flush>
                            {list.length === 0 ? (
                                <p className="p-4 text-muted">Prowadzący nie wystawił jeszcze ocen końcowych.</p>
                            ) : (
                                <Table minWidth={640}>
                                    <thead>
                                    <tr>
                                        <Th>Student</Th>
                                        <Th>Termin</Th>
                                        <Th>Ocena</Th>
                                        <Th>Zatwierdzono</Th>
                                        <Th />
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {list.map((g) => (
                                        <Tr key={g.id}>
                                            <Td>
                                                {g.studentLastName} {g.studentFirstName}
                                                <div className="text-xs text-muted">{g.albumNumber}</div>
                                            </Td>
                                            <Td>{ATTEMPT_LABELS[g.attempt]}</Td>
                                            <Td className={`font-semibold ${g.passing ? '' : 'text-red-700'}`}>{g.gradeLabel}</Td>
                                            <Td>
                                                {g.approvedAt ? (
                                                    formatInstant(g.approvedAt)
                                                ) : (
                                                    <span className="text-muted">szkic prowadzącego</span>
                                                )}
                                            </Td>
                                            <Td className="text-right">
                                                {g.status === 'APPROVED' && latest.get(g.enrollmentId)?.id === g.id && scale && (
                                                    <Button
                                                        type="button"
                                                        variant="secondary"
                                                        className="px-3 py-1 text-sm"
                                                        onClick={() => {
                                                            setNotice(null);
                                                            setCorrecting(g);
                                                        }}
                                                    >
                                                        Koryguj
                                                    </Button>
                                                )}
                                            </Td>
                                        </Tr>
                                    ))}
                                    </tbody>
                                </Table>
                            )}
                        </Panel>
                        {correcting && scale && (
                            <CorrectionDialog
                                grade={correcting}
                                scale={scale}
                                gradesKey={['dean', 'groups', groupId, 'final-grades']}
                                onClose={() => setCorrecting(null)}
                                onCorrected={(label) =>
                                    setNotice(
                                        `Skorygowano ocenę: ${correcting.studentFirstName} ${correcting.studentLastName}, ${label}.`,
                                    )
                                }
                            />
                        )}
                    </div>
                );
            }}
        </QueryState>
    );
}

function CorrectionDialog({
    grade,
    scale,
    gradesKey,
    onClose,
    onCorrected,
}: {
    grade: FinalGrade;
    scale: GradingScale;
    gradesKey: string[];
    onClose: () => void;
    onCorrected: (label: string) => void;
}) {
    const queryClient = useQueryClient();
    const [text, setText] = useState('');
    const [reason, setReason] = useState('');
    const value = parseGrade(text, scale);
    const unchanged = value?.label === grade.gradeLabel;

    const correct = useMutation({
        mutationFn: () =>
            api(`/api/dean-office/final-grades/${encodeURIComponent(grade.id)}/correction`, {
                method: 'POST',
                body: JSON.stringify({ gradeValueId: value!.id, reason: reason.trim() }),
            }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: gradesKey });
            onCorrected(value!.label);
            onClose();
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        correct.mutate();
    }

    return (
        <Dialog open onClose={onClose} title="Korekta zatwierdzonej oceny końcowej">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <p>
                    <strong>
                        {grade.studentFirstName} {grade.studentLastName}
                    </strong>{' '}
                    ({grade.albumNumber}), {ATTEMPT_LABELS[grade.attempt]}
                    <br />
                    Zatwierdzona ocena: <strong>{grade.gradeLabel}</strong>
                </p>
                <div>
                    <TextField
                        label="Prawidłowa ocena"
                        required
                        autoFocus
                        autoComplete="off"
                        value={text}
                        onChange={(e) => setText(e.currentTarget.value)}
                    />
                    <p className="mt-1 text-sm text-muted">
                        {value ? (
                            <>
                                Zostanie zapisane: <strong className="text-ink">{value.label}</strong>
                            </>
                        ) : value === null ? (
                            <span className="text-red-700">Nie ma takiej oceny w skali. Dozwolone: {gradeHint(scale)}.</span>
                        ) : (
                            <>Dozwolone: {gradeHint(scale)}</>
                        )}
                    </p>
                </div>
                <TextField
                    label="Podstawa korekty"
                    required
                    maxLength={500}
                    placeholder="np. wniosek prowadzącego z 12.11.2026"
                    value={reason}
                    onChange={(e) => setReason(e.currentTarget.value)}
                />
                <p className="text-sm text-muted">
                    Korekta zmienia ocenę w indeksie studenta. Poprzednia wartość, osoba korygująca i podstawa korekty
                    zostają zapisane w dzienniku audytu.
                </p>
                {unchanged && <Alert>To ta sama ocena, która jest już zatwierdzona.</Alert>}
                {correct.isError && <Alert>{errorMessage(correct.error)}</Alert>}
                <div className="flex justify-end gap-2">
                    <Button type="button" variant="secondary" onClick={onClose}>
                        Anuluj
                    </Button>
                    <Button type="submit" loading={correct.isPending} disabled={!value || unchanged || !reason.trim()}>
                        Zapisz korektę
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}
