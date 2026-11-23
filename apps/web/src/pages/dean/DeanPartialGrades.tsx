import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { AssessmentItem, ClassGroup, GradingScale, PartialGrade, RosterEntry } from '../../api/types';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { TextField } from '../../components/ui/TextField';
import { errorMessage } from '../../i18n/errors';
import { gradeHint, parseGrade } from '../../i18n/grades';
import { splitAttempts } from '../../lib/attempts';
import { sessionSummary } from '../../lib/corrections';

interface Target {
    student: RosterEntry;
    item: AssessmentItem;
    grade: PartialGrade;
}

/**
 * Oceny cząstkowe grupy w dziekanacie: podgląd z historią popraw i korekta pomyłki,
 * także po terminie prowadzącego – zawsze z podstawą korekty.
 */
export function DeanPartialGrades({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const gradesKey = ['dean', 'groups', groupId, 'partial-grades'];
    const roster = useQuery({
        queryKey: ['groups', groupId, 'students'],
        queryFn: () => api<RosterEntry[]>(`/api/groups/${groupId}/students`),
    });
    const items = useQuery({
        queryKey: ['groups', groupId, 'items'],
        queryFn: () => api<AssessmentItem[]>(`/api/groups/${groupId}/assessment-items`),
    });
    const grades = useQuery({
        queryKey: gradesKey,
        queryFn: () => api<PartialGrade[]>(`/api/dean-office/groups/${groupId}/partial-grades`),
    });
    const scales = useQuery({ queryKey: ['grading-scales'], queryFn: () => api<GradingScale[]>('/api/grading-scales') });
    const [target, setTarget] = useState<Target | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    return (
        <QueryState query={roster}>
            {(students) => (
                <QueryState query={items}>
                    {(itemList) => (
                        <QueryState query={grades}>
                            {(gradeList) => {
                                if (itemList.length === 0) {
                                    return <p className="text-muted">Prowadzący nie dodał jeszcze elementów zaliczenia.</p>;
                                }
                                return (
                                    <div className="flex flex-col gap-4">
                                        {notice && <Alert variant="success">{notice}</Alert>}
                                        <Panel title="Oceny cząstkowe" flush>
                                            <Table minWidth={240 + itemList.length * 150}>
                                                <thead>
                                                <tr>
                                                    <Th>Student</Th>
                                                    {itemList.map((item) => (
                                                        <Th key={item.id}>
                                                            {item.name}
                                                            <div className="text-xs font-normal text-muted">
                                                                {item.gradingType === 'PASS_FAIL'
                                                                    ? 'zal/nzal'
                                                                    : `ocena, waga ${item.weightPercent}%`}
                                                            </div>
                                                        </Th>
                                                    ))}
                                                </tr>
                                                </thead>
                                                <tbody>
                                                {students.map((student) => (
                                                    <Tr key={student.enrollmentId}>
                                                        <Td>
                                                            {student.lastName} {student.firstName}
                                                            <div className="text-xs text-muted">{student.albumNumber}</div>
                                                        </Td>
                                                        {itemList.map((item) => {
                                                            const { current, earlier } = splitAttempts(
                                                                gradeList.filter(
                                                                    (g) =>
                                                                        g.enrollmentId === student.enrollmentId &&
                                                                        g.assessmentItemId === item.id,
                                                                ),
                                                            );
                                                            return (
                                                                <Td key={item.id}>
                                                                    {current ? (
                                                                        <span className="inline-flex flex-wrap items-baseline gap-x-1">
                                                                            {earlier.map((g) => (
                                                                                <span key={g.id} className="text-sm text-muted">
                                                                                    {g.gradeLabel} →
                                                                                </span>
                                                                            ))}
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => {
                                                                                    setNotice(null);
                                                                                    setTarget({ student, item, grade: current });
                                                                                }}
                                                                                aria-label={`Korekta oceny ${current.gradeLabel}: ${student.firstName} ${student.lastName}, ${item.name}`}
                                                                                className={`rounded-sm px-1 font-semibold underline decoration-line decoration-dotted underline-offset-4 hover:bg-page hover:decoration-navy ${current.passing ? '' : 'text-red-700'}`}
                                                                            >
                                                                                {current.gradeLabel}
                                                                            </button>
                                                                        </span>
                                                                    ) : (
                                                                        <span className="text-muted">–</span>
                                                                    )}
                                                                </Td>
                                                            );
                                                        })}
                                                    </Tr>
                                                ))}
                                                </tbody>
                                            </Table>
                                            <p className="border-t border-line px-4 py-2 text-sm text-muted">
                                                {sessionSummary(group, false)} Kliknij ocenę i podaj podstawę
                                                korekty.
                                            </p>
                                        </Panel>
                                        {target && scales.data && (
                                            <DeanCorrectionDialog
                                                target={target}
                                                scale={scales.data.find((s) => s.id === target.item.gradingScaleId)!}
                                                gradesKey={gradesKey}
                                                onClose={() => setTarget(null)}
                                                onCorrected={(label) =>
                                                    setNotice(
                                                        `Skorygowano ocenę: ${target.student.firstName} ${target.student.lastName}, ${target.item.name}, ${label}.`,
                                                    )
                                                }
                                            />
                                        )}
                                    </div>
                                );
                            }}
                        </QueryState>
                    )}
                </QueryState>
            )}
        </QueryState>
    );
}

function DeanCorrectionDialog({
    target,
    scale,
    gradesKey,
    onClose,
    onCorrected,
}: {
    target: Target;
    scale: GradingScale;
    gradesKey: string[];
    onClose: () => void;
    onCorrected: (label: string) => void;
}) {
    const queryClient = useQueryClient();
    const [text, setText] = useState('');
    const [reason, setReason] = useState('');
    const value = parseGrade(text, scale);
    const unchanged = value?.label === target.grade.gradeLabel;

    const correct = useMutation({
        mutationFn: () =>
            api(`/api/dean-office/partial-grades/${encodeURIComponent(target.grade.id)}/correction`, {
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
        <Dialog open onClose={onClose} title="Korekta oceny cząstkowej">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <p>
                    <strong>
                        {target.student.firstName} {target.student.lastName}
                    </strong>{' '}
                    ({target.student.albumNumber}), {target.item.name}
                    <br />
                    Wpisana ocena: <strong>{target.grade.gradeLabel}</strong>
                    {target.grade.attempt > 1 && <> (próba {target.grade.attempt})</>}
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
                    placeholder="np. wniosek prowadzącego z 20.11.2026"
                    value={reason}
                    onChange={(e) => setReason(e.currentTarget.value)}
                />
                <p className="text-sm text-muted">
                    Korekta zastępuje ocenę bez tworzenia nowej próby. Poprzednia wartość, osoba korygująca i podstawa korekty
                    zostają zapisane w dzienniku audytu.
                </p>
                {unchanged && <Alert>To ta sama ocena, która jest już wpisana.</Alert>}
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
