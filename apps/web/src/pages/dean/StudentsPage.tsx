import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { StudyRecord, StudyStatus, UnassignedStudent } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/PageHeader';
import { ResetPasswordDialog } from '../../components/ResetPasswordDialog';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Select } from '../../components/ui/Select';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { TextField } from '../../components/ui/TextField';
import { errorMessage } from '../../i18n/errors';
import { STUDY_STATUS_LABELS } from '../../i18n/labels';
import { useFacultyCatalog } from './catalog';
import { StudentImportPanel } from './StudentImportPanel';

const STUDIES_KEY = ['dean', 'studies'];
const UNASSIGNED_KEY = ['dean', 'students', 'unassigned'];

/** Rok akademicki zaczyna się w październiku: we wrześniu i później – bieżący rok, wcześniej – poprzedni. */
function academicStartYear(): number {
    const now = new Date();
    return now.getMonth() >= 8 ? now.getFullYear() : now.getFullYear() - 1;
}

export function StudentsPage() {
    const { user } = useAuth();
    const catalog = useFacultyCatalog(user?.facultyId ?? null);
    const studies = useQuery({ queryKey: STUDIES_KEY, queryFn: () => api<StudyRecord[]>('/api/dean-office/studies') });
    const [editing, setEditing] = useState<StudyRecord | null>(null);
    const [resetting, setResetting] = useState<StudyRecord | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    return (
        <>
            <PageHeader title="Studenci" crumbs={[{ label: 'Studenci' }]} />
            <div className="flex flex-col gap-6">
                {catalog.data && (
                    <StudentImportPanel
                        fields={catalog.data.fields}
                        defaultStartYear={academicStartYear()}
                        invalidateKeys={[STUDIES_KEY]}
                    />
                )}
                <UnassignedPanel onAssigned={(text) => setNotice(text)} />
                <QueryState query={studies}>
                    {(list) => (
                        <StudyList
                            list={list}
                            notice={notice}
                            onNotice={setNotice}
                            onEdit={(s) => {
                                setNotice(null);
                                setEditing(s);
                            }}
                            onResetPassword={(s) => {
                                setNotice(null);
                                setResetting(s);
                            }}
                        />
                    )}
                </QueryState>
            </div>
            {editing && (
                <StudyDialog
                    study={editing}
                    onClose={() => setEditing(null)}
                    onSaved={() => setNotice(`Zapisano zmiany: ${editing.studentFirstName} ${editing.studentLastName}.`)}
                />
            )}
            {resetting && (
                <ResetPasswordDialog
                    endpoint={`/api/dean-office/studies/${encodeURIComponent(resetting.id)}/reset-password`}
                    person={{
                        name: `${resetting.studentFirstName} ${resetting.studentLastName}`,
                        detail: `album ${resetting.albumNumber}`,
                    }}
                    onClose={() => setResetting(null)}
                />
            )}
        </>
    );
}

/**
 * Lista studentów wydziału z filtrami i zmianami dla wielu osób naraz: zaznacz (np. cały semestr
 * filtrem i „zaznacz wszystkich”), potem jedno kliknięcie – przeniesienie na kolejny semestr albo status.
 */
function StudyList({
    list,
    notice,
    onNotice,
    onEdit,
    onResetPassword,
}: {
    list: StudyRecord[];
    notice: string | null;
    onNotice: (text: string | null) => void;
    onEdit: (study: StudyRecord) => void;
    onResetPassword: (study: StudyRecord) => void;
}) {
    const queryClient = useQueryClient();
    const [search, setSearch] = useState('');
    const [field, setField] = useState('');
    const [semester, setSemester] = useState('');
    const [status, setStatus] = useState<StudyStatus | ''>('');
    const [selected, setSelected] = useState<Set<string>>(new Set());
    const [newStatus, setNewStatus] = useState<StudyStatus>('DEAN_LEAVE');

    const phrase = search.trim().toLowerCase();
    const visible = list.filter(
        (s) =>
            (!field || s.fieldOfStudyName === field) &&
            (!semester || s.currentSemester === Number(semester)) &&
            (!status || s.status === status) &&
            (!phrase || `${s.studentLastName} ${s.studentFirstName} ${s.albumNumber}`.toLowerCase().includes(phrase)),
    );
    const fieldNames = [...new Set(list.map((s) => s.fieldOfStudyName))].sort((a, b) => a.localeCompare(b, 'pl'));
    const semesters = [...new Set(list.map((s) => s.currentSemester))].sort((a, b) => a - b);
    const selectedVisible = visible.filter((s) => selected.has(s.id));
    const allVisibleSelected = visible.length > 0 && selectedVisible.length === visible.length;

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
        setSelected(allVisibleSelected ? new Set() : new Set(visible.map((s) => s.id)));
    }

    const batch = useMutation({
        mutationFn: (change: { promote: boolean; status: StudyStatus | null }) =>
            api<StudyRecord[]>('/api/dean-office/studies/batch-update', {
                method: 'POST',
                body: JSON.stringify({ studyIds: selectedVisible.map((s) => s.id), ...change }),
            }),
        onSuccess: async (updated, change) => {
            await queryClient.invalidateQueries({ queryKey: STUDIES_KEY });
            setSelected(new Set());
            onNotice(
                change.promote
                    ? `Przeniesiono na kolejny semestr: ${updated.length}.`
                    : `Ustawiono status „${STUDY_STATUS_LABELS[change.status!]}”: ${updated.length}.`,
            );
        },
    });

    return (
        <div className="flex flex-col gap-4">
            <div className="grid gap-4 sm:grid-cols-[2fr_1.5fr_1fr_1fr]">
                <TextField
                    label="Szukaj"
                    placeholder="nazwisko, imię lub numer albumu"
                    value={search}
                    onChange={(e) => setSearch(e.currentTarget.value)}
                />
                <Select label="Kierunek" value={field} onChange={(e) => setField(e.currentTarget.value)}>
                    <option value="">wszystkie</option>
                    {fieldNames.map((name) => (
                        <option key={name} value={name}>
                            {name}
                        </option>
                    ))}
                </Select>
                <Select label="Semestr" value={semester} onChange={(e) => setSemester(e.currentTarget.value)}>
                    <option value="">wszystkie</option>
                    {semesters.map((n) => (
                        <option key={n} value={n}>
                            {n}
                        </option>
                    ))}
                </Select>
                <Select label="Status" value={status} onChange={(e) => setStatus(e.currentTarget.value as StudyStatus | '')}>
                    <option value="">wszystkie</option>
                    {(Object.keys(STUDY_STATUS_LABELS) as StudyStatus[]).map((s) => (
                        <option key={s} value={s}>
                            {STUDY_STATUS_LABELS[s]}
                        </option>
                    ))}
                </Select>
            </div>
            {notice && <Alert variant="success">{notice}</Alert>}
            <Panel
                title="Studenci wydziału"
                flush
                aside={
                    <span className="text-sm">
                        {visible.length === list.length ? list.length : `${visible.length} z ${list.length}`}
                    </span>
                }
            >
                {visible.length === 0 ? (
                    <p className="p-4 text-muted">Brak studentów spełniających kryteria.</p>
                ) : (
                    <Table minWidth={760}>
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
                            <Th>Kierunek</Th>
                            <Th>Rok rozpoczęcia</Th>
                            <Th>Semestr</Th>
                            <Th>Status</Th>
                            <Th />
                        </tr>
                        </thead>
                        <tbody>
                        {visible.map((s) => (
                            <Tr key={s.id}>
                                <Td>
                                    <input
                                        type="checkbox"
                                        id={`row-${s.id}`}
                                        checked={selected.has(s.id)}
                                        onChange={() => toggle(s.id)}
                                        className="accent-navy"
                                    />
                                </Td>
                                <Td>
                                    <label htmlFor={`row-${s.id}`} className="cursor-pointer">
                                        {s.studentLastName} {s.studentFirstName}
                                    </label>
                                </Td>
                                <Td>{s.albumNumber}</Td>
                                <Td>{s.fieldOfStudyName}</Td>
                                <Td>{s.startYear}</Td>
                                <Td>{s.currentSemester}</Td>
                                <Td className={s.status === 'ACTIVE' ? '' : 'text-muted'}>{STUDY_STATUS_LABELS[s.status]}</Td>
                                <Td className="text-right whitespace-nowrap">
                                    <Button type="button" variant="secondary" className="px-3 py-1 text-sm" onClick={() => onEdit(s)}>
                                        Zmień
                                    </Button>{' '}
                                    <Button
                                        type="button"
                                        variant="secondary"
                                        className="px-3 py-1 text-sm"
                                        aria-label={`Nowe hasło: ${s.studentFirstName} ${s.studentLastName}`}
                                        onClick={() => onResetPassword(s)}
                                    >
                                        Nowe hasło
                                    </Button>
                                </Td>
                            </Tr>
                        ))}
                        </tbody>
                    </Table>
                )}

                {selectedVisible.length > 0 && (
                    <div className="sticky bottom-0 flex flex-wrap items-center gap-3 border-t border-line bg-white px-4 py-3">
                        <span className="text-sm font-semibold">Zaznaczono: {selectedVisible.length}</span>
                        <Button
                            type="button"
                            loading={batch.isPending && batch.variables?.promote === true}
                            disabled={batch.isPending}
                            onClick={() => batch.mutate({ promote: true, status: null })}
                        >
                            Przenieś na kolejny semestr
                        </Button>
                        <label className="flex items-center gap-2 text-sm">
                            Status:
                            <select
                                value={newStatus}
                                onChange={(e) => setNewStatus(e.currentTarget.value as StudyStatus)}
                                className="rounded-sm border border-line bg-white px-2 py-1.5"
                            >
                                {(Object.keys(STUDY_STATUS_LABELS) as StudyStatus[]).map((s) => (
                                    <option key={s} value={s}>
                                        {STUDY_STATUS_LABELS[s]}
                                    </option>
                                ))}
                            </select>
                        </label>
                        <Button
                            type="button"
                            variant="secondary"
                            loading={batch.isPending && batch.variables?.promote === false}
                            disabled={batch.isPending}
                            onClick={() => batch.mutate({ promote: false, status: newStatus })}
                        >
                            Ustaw status
                        </Button>
                        <Button type="button" variant="secondary" onClick={() => setSelected(new Set())}>
                            Odznacz
                        </Button>
                        <p className="w-full text-sm text-muted">
                            Zmiana obejmuje wszystkich zaznaczonych albo nikogo – np. gdy ktoś jest już na ostatnim semestrze
                            kierunku, nikt nie zostanie przeniesiony. Każda zmiana trafia do dziennika audytu.
                        </p>
                        {batch.isError && <Alert>{errorMessage(batch.error)}</Alert>}
                    </div>
                )}
            </Panel>
        </div>
    );
}

/**
 * Nowe konta od administratora czekają tu na kierunek. Kierunek, rok i semestr ustawia się raz
 * nad listą, a potem każda osoba to jedno kliknięcie „Przypisz”.
 */
function UnassignedPanel({ onAssigned }: { onAssigned: (text: string) => void }) {
    const { user } = useAuth();
    const queryClient = useQueryClient();
    const students = useQuery({
        queryKey: UNASSIGNED_KEY,
        queryFn: () => api<UnassignedStudent[]>('/api/dean-office/students/unassigned'),
    });
    const catalog = useFacultyCatalog(user?.facultyId ?? null);
    const fields = catalog.data?.fields ?? [];
    const [chosenFieldId, setFieldId] = useState('');
    const [startYear, setStartYear] = useState(String(academicStartYear()));
    const [semester, setSemester] = useState('1');
    const fieldId = chosenFieldId || fields[0]?.id || '';

    const assign = useMutation({
        mutationFn: (student: UnassignedStudent) =>
            api(`/api/dean-office/studies`, {
                method: 'POST',
                body: JSON.stringify({
                    studentId: student.id,
                    fieldOfStudyId: fieldId,
                    startYear: Number(startYear),
                    currentSemester: Number(semester),
                }),
            }),
        onSuccess: async (_, student) => {
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: UNASSIGNED_KEY }),
                queryClient.invalidateQueries({ queryKey: STUDIES_KEY }),
            ]);
            const field = fields.find((f) => f.id === fieldId)?.name ?? '';
            onAssigned(`Przypisano: ${student.firstName} ${student.lastName}, ${field}, semestr ${semester}.`);
        },
    });

    if (!students.data || students.data.length === 0) {
        return null;
    }

    return (
        <div className="mb-6">
            <Panel
                title="Nowi studenci bez kierunku"
                flush
                aside={<span className="text-sm">{students.data.length}</span>}
            >
                <div className="grid gap-4 border-b border-line p-4 sm:grid-cols-[2fr_1fr_1fr]">
                    <Select label="Kierunek" value={fieldId} onChange={(e) => setFieldId(e.currentTarget.value)}>
                        {fields.map((f) => (
                            <option key={f.id} value={f.id}>
                                {f.name}
                            </option>
                        ))}
                    </Select>
                    <TextField
                        label="Rok rozpoczęcia"
                        type="number"
                        min={2000}
                        max={2100}
                        value={startYear}
                        onChange={(e) => setStartYear(e.currentTarget.value)}
                    />
                    <TextField
                        label="Semestr"
                        type="number"
                        min={1}
                        max={12}
                        value={semester}
                        onChange={(e) => setSemester(e.currentTarget.value)}
                    />
                </div>
                <Table>
                    <thead>
                    <tr>
                        <Th>Student</Th>
                        <Th>Numer albumu</Th>
                        <Th />
                    </tr>
                    </thead>
                    <tbody>
                    {students.data.map((s) => (
                        <Tr key={s.id}>
                            <Td>
                                {s.lastName} {s.firstName}
                            </Td>
                            <Td>{s.albumNumber}</Td>
                            <Td className="text-right">
                                <Button
                                    type="button"
                                    className="px-3 py-1 text-sm"
                                    disabled={!fieldId || assign.isPending}
                                    loading={assign.isPending && assign.variables?.id === s.id}
                                    onClick={() => assign.mutate(s)}
                                >
                                    Przypisz
                                </Button>
                            </Td>
                        </Tr>
                    ))}
                    </tbody>
                </Table>
                {assign.isError && (
                    <div className="p-4">
                        <Alert>{errorMessage(assign.error)}</Alert>
                    </div>
                )}
            </Panel>
        </div>
    );
}

function StudyDialog({ study, onClose, onSaved }: { study: StudyRecord; onClose: () => void; onSaved: () => void }) {
    const queryClient = useQueryClient();
    const [semester, setSemester] = useState(String(study.currentSemester));
    const [status, setStatus] = useState<StudyStatus>(study.status);

    const save = useMutation({
        mutationFn: () =>
            api(`/api/dean-office/studies/${encodeURIComponent(study.id)}`, {
                method: 'PUT',
                body: JSON.stringify({ currentSemester: Number(semester), status }),
            }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: STUDIES_KEY });
            onSaved();
            onClose();
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        save.mutate();
    }

    return (
        <Dialog open onClose={onClose} title="Przebieg studiów">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <p>
                    <strong>
                        {study.studentFirstName} {study.studentLastName}
                    </strong>{' '}
                    ({study.albumNumber}), {study.fieldOfStudyName}
                </p>
                <div className="grid gap-4 sm:grid-cols-2">
                    <TextField
                        label="Semestr"
                        type="number"
                        required
                        autoFocus
                        min={1}
                        max={12}
                        value={semester}
                        onChange={(e) => setSemester(e.currentTarget.value)}
                    />
                    <Select label="Status" value={status} onChange={(e) => setStatus(e.currentTarget.value as StudyStatus)}>
                        {(Object.keys(STUDY_STATUS_LABELS) as StudyStatus[]).map((s) => (
                            <option key={s} value={s}>
                                {STUDY_STATUS_LABELS[s]}
                            </option>
                        ))}
                    </Select>
                </div>
                <p className="text-sm text-muted">
                    Student na urlopie, skreślony lub absolwent nie może być zapisywany do nowych grup. Zmiana trafia do
                    dziennika audytu.
                </p>
                {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
                <div className="flex justify-end gap-2">
                    <Button type="button" variant="secondary" onClick={onClose}>
                        Anuluj
                    </Button>
                    <Button type="submit" loading={save.isPending}>
                        Zapisz
                    </Button>
                </div>
            </form>
        </Dialog>
    );
}
