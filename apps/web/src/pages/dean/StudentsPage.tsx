import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { StudyRecord, StudyStatus } from '../../api/types';
import { PageHeader } from '../../components/PageHeader';
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

const STUDIES_KEY = ['dean', 'studies'];

export function StudentsPage() {
    const studies = useQuery({ queryKey: STUDIES_KEY, queryFn: () => api<StudyRecord[]>('/api/dean-office/studies') });
    const [search, setSearch] = useState('');
    const [editing, setEditing] = useState<StudyRecord | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    return (
        <>
            <PageHeader title="Studenci" crumbs={[{ label: 'Studenci' }]} />
            <QueryState query={studies}>
                {(list) => {
                    const phrase = search.trim().toLowerCase();
                    const visible = phrase
                        ? list.filter((s) =>
                              `${s.studentLastName} ${s.studentFirstName} ${s.albumNumber} ${s.fieldOfStudyName}`
                                  .toLowerCase()
                                  .includes(phrase),
                          )
                        : list;
                    return (
                        <div className="flex flex-col gap-4">
                            <div className="max-w-md">
                                <TextField
                                    label="Szukaj"
                                    placeholder="nazwisko, imię, numer albumu lub kierunek"
                                    value={search}
                                    onChange={(e) => setSearch(e.currentTarget.value)}
                                />
                            </div>
                            {notice && <Alert variant="success">{notice}</Alert>}
                            <Panel
                                title="Studenci wydziału"
                                flush
                                aside={<span className="text-sm">{visible.length === list.length ? list.length : `${visible.length} z ${list.length}`}</span>}
                            >
                                {visible.length === 0 ? (
                                    <p className="p-4 text-muted">Brak studentów spełniających kryteria.</p>
                                ) : (
                                    <Table minWidth={720}>
                                        <thead>
                                        <tr>
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
                                                    {s.studentLastName} {s.studentFirstName}
                                                </Td>
                                                <Td>{s.albumNumber}</Td>
                                                <Td>{s.fieldOfStudyName}</Td>
                                                <Td>{s.startYear}</Td>
                                                <Td>{s.currentSemester}</Td>
                                                <Td className={s.status === 'ACTIVE' ? '' : 'text-muted'}>
                                                    {STUDY_STATUS_LABELS[s.status]}
                                                </Td>
                                                <Td className="text-right">
                                                    <Button
                                                        type="button"
                                                        variant="secondary"
                                                        className="px-3 py-1 text-sm"
                                                        onClick={() => {
                                                            setNotice(null);
                                                            setEditing(s);
                                                        }}
                                                    >
                                                        Zmień
                                                    </Button>
                                                </Td>
                                            </Tr>
                                        ))}
                                        </tbody>
                                    </Table>
                                )}
                            </Panel>
                        </div>
                    );
                }}
            </QueryState>
            {editing && (
                <StudyDialog
                    study={editing}
                    onClose={() => setEditing(null)}
                    onSaved={() => setNotice(`Zapisano zmiany: ${editing.studentFirstName} ${editing.studentLastName}.`)}
                />
            )}
        </>
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
