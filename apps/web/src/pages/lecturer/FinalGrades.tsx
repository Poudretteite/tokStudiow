import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { api } from '../../api/client';
import type { ClassGroup, FinalGradeSheetRow, GradeValue, GradingScale } from '../../api/types';
import { Panel } from '../../components/Panel';
import { QueryState } from '../../components/QueryState';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Dialog } from '../../components/ui/Dialog';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { errorMessage } from '../../i18n/errors';
import { formatAverage } from '../../i18n/format';
import { gradeHint, parseGrade } from '../../i18n/grades';
import { ATTEMPT_LABELS } from '../../i18n/labels';

export function FinalGrades({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const sheetKey = ['lecturer', 'groups', groupId, 'final-grade-sheet'];

    const sheet = useQuery({
        queryKey: sheetKey,
        queryFn: () => api<FinalGradeSheetRow[]>(`/api/lecturer/groups/${groupId}/final-grade-sheet`),
    });
    const scales = useQuery({ queryKey: ['grading-scales'], queryFn: () => api<GradingScale[]>('/api/grading-scales') });
    const scale = scales.data?.find((s) => s.id === group.gradingScaleId);

    return (
        <QueryState query={sheet}>
            {(rows) => (scale ? <FinalGradeSheet groupId={groupId} rows={rows} scale={scale} sheetKey={sheetKey} /> : null)}
        </QueryState>
    );
}

interface SheetProps {
    groupId: string;
    rows: FinalGradeSheetRow[];
    scale: GradingScale;
    sheetKey: string[];
}

/**
 * Cała grupa na jednym ekranie: propozycja jest od razu wpisana jako ocena,
 * prowadzący poprawia tylko wyjątki i zatwierdza wszystko jednym przyciskiem.
 */
function FinalGradeSheet({ groupId, rows, scale, sheetKey }: SheetProps) {
    const queryClient = useQueryClient();
    const [edits, setEdits] = useState<Record<string, string>>({});
    const [confirming, setConfirming] = useState(false);
    const [notice, setNotice] = useState<string | null>(null);
    const inputs = useRef<(HTMLInputElement | null)[]>([]);
    const approveButton = useRef<HTMLButtonElement>(null);

    const labelOf = (id: string | null) => scale.values.find((v) => v.id === id)?.label ?? '';

    // Domyślnie: zapisany szkic, a w I terminie propozycja z ocen cząstkowych.
    // Poprawki i komisji nie wypełniamy – wynik zależy od nowego sprawdzianu.
    const defaultText = (row: FinalGradeSheetRow) =>
        row.draftGradeValueId
            ? labelOf(row.draftGradeValueId)
            : row.openAttempt === 'FIRST'
                ? (row.proposedGradeLabel ?? '')
                : '';

    const open = rows.filter((row) => row.openAttempt !== null);
    const entries = open.map((row) => ({ row, value: parseGrade(edits[row.enrollmentId] ?? defaultText(row), scale) }));
    const toApprove = entries.filter((e): e is { row: FinalGradeSheetRow; value: GradeValue } => Boolean(e.value));
    const hasInvalid = entries.some((e) => e.value === null);
    const failing = toApprove.filter((e) => !e.value.passing).length;

    const approve = useMutation({
        mutationFn: () =>
            api(`/api/lecturer/groups/${groupId}/final-grades/approvals`, {
                method: 'POST',
                body: JSON.stringify({
                    entries: toApprove.map((e) => ({ enrollmentId: e.row.enrollmentId, gradeValueId: e.value.id })),
                }),
            }),
        onSuccess: async () => {
            const count = toApprove.length;
            await queryClient.invalidateQueries({ queryKey: sheetKey });
            setEdits({});
            setConfirming(false);
            setNotice(`Zatwierdzono oceny końcowe: ${count}.`);
        },
    });

    if (rows.length === 0) {
        return <p className="text-muted">Do grupy nie zapisano jeszcze studentów.</p>;
    }

    function handleKeyDown(event: KeyboardEvent<HTMLInputElement>, index: number) {
        if (event.key === 'Enter' || event.key === 'ArrowDown') {
            event.preventDefault();
            const next = inputs.current[index + 1];
            if (next) {
                next.focus();
            } else if (event.key === 'Enter') {
                approveButton.current?.focus();
            }
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            inputs.current[index - 1]?.focus();
        }
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        approve.reset();
        setNotice(null);
        setConfirming(true);
    }

    const noProposals = rows.every((row) => row.proposedGradeLabel === null);

    return (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {notice && <Alert variant="success">{notice}</Alert>}

            <Panel title="Oceny końcowe" flush>
                <Table minWidth={720}>
                    <thead>
                    <tr>
                        <Th>Student</Th>
                        <Th>Średnia ważona</Th>
                        <Th>Propozycja</Th>
                        <Th>Wcześniejsze terminy</Th>
                        <Th>
                            Ocena końcowa
                            <div className="text-xs font-normal text-muted">{gradeHint(scale)}</div>
                        </Th>
                    </tr>
                    </thead>
                    <tbody>
                    {rows.map((row) => {
                        const openIndex = open.indexOf(row);
                        const entry = openIndex >= 0 ? entries[openIndex] : undefined;
                        const approved = row.grades.filter((g) => g.status === 'APPROVED');
                        const earlier = row.openAttempt === null ? approved.slice(0, -1) : approved;
                        const settled = row.openAttempt === null ? approved.at(-1) : undefined;

                        return (
                            <Tr key={row.enrollmentId}>
                                <Td>
                                    {row.studentLastName} {row.studentFirstName}
                                    <div className="text-xs text-muted">{row.albumNumber}</div>
                                </Td>
                                <Td>{row.weightedAverage === null ? '–' : formatAverage(row.weightedAverage)}</Td>
                                <Td>{row.proposedGradeLabel ?? <span className="text-muted">brak</span>}</Td>
                                <Td className="text-sm">
                                    {earlier.length === 0 ? (
                                        <span className="text-muted">–</span>
                                    ) : (
                                        earlier.map((g) => (
                                            <div key={g.id}>
                                                {ATTEMPT_LABELS[g.attempt]}:{' '}
                                                <span className={g.passing ? '' : 'text-red-700'}>{g.gradeLabel}</span>
                                            </div>
                                        ))
                                    )}
                                </Td>
                                <Td>
                                    {settled ? (
                                        <>
                                                <span className={`font-semibold ${settled.passing ? '' : 'text-red-700'}`}>
                                                    {settled.gradeLabel}
                                                </span>
                                            <div className="text-xs text-muted">
                                                zatwierdzona ({ATTEMPT_LABELS[settled.attempt]})
                                            </div>
                                        </>
                                    ) : entry ? (
                                        <GradeInput
                                            row={row}
                                            value={entry.value}
                                            text={edits[row.enrollmentId] ?? defaultText(row)}
                                            inputRef={(el) => {
                                                inputs.current[openIndex] = el;
                                            }}
                                            numeric={scale.type === 'NUMERIC'}
                                            onChange={(text) => setEdits((e) => ({ ...e, [row.enrollmentId]: text }))}
                                            onKeyDown={(event) => handleKeyDown(event, openIndex)}
                                        />
                                    ) : null}
                                </Td>
                            </Tr>
                        );
                    })}
                    </tbody>
                </Table>

                {open.length > 0 && (
                    <div className="sticky bottom-0 flex flex-wrap items-center gap-3 border-t border-line bg-white px-4 py-3">
                        <Button ref={approveButton} type="submit" disabled={toApprove.length === 0 || hasInvalid}>
                            Zatwierdź oceny ({toApprove.length})
                        </Button>
                        <p className="text-sm text-muted">
                            Propozycje są już wpisane, zmień tylko wyjątki. Puste pola zostaną pominięte – wyczyść
                            pole, aby nie zatwierdzać oceny tej osoby.
                        </p>
                        {noProposals && (
                            <p className="w-full text-sm text-muted">
                                Brak propozycji: uzupełnij oceny cząstkowe wszystkich elementów, a wagi elementów na
                                ocenę muszą dawać razem 100%.
                            </p>
                        )}
                        {hasInvalid && <Alert>Popraw oceny zaznaczone na czerwono.</Alert>}
                    </div>
                )}
            </Panel>

            {confirming && (
                <Dialog open onClose={() => setConfirming(false)} title="Zatwierdzenie ocen końcowych">
                    <div className="flex flex-col gap-4">
                        <p>
                            Zatwierdzasz <strong>{toApprove.length}</strong> {gradesWord(toApprove.length)}
                            {failing > 0 && (
                                <>
                                    , w tym niezaliczone: <strong className="text-red-700">{failing}</strong>
                                </>
                            )}
                            .
                        </p>
                        <p className="text-sm text-muted">
                            Zatwierdzone oceny trafią do indeksu studentów i nie będzie można ich zmienić. Oceny
                            cząstkowe tych osób zostaną zablokowane.
                        </p>
                        {approve.isError && <Alert>{errorMessage(approve.error)}</Alert>}
                        <div className="flex justify-end gap-2">
                            <Button type="button" variant="secondary" onClick={() => setConfirming(false)}>
                                Anuluj
                            </Button>
                            <Button type="button" autoFocus loading={approve.isPending} onClick={() => approve.mutate()}>
                                Zatwierdź ({toApprove.length})
                            </Button>
                        </div>
                    </div>
                </Dialog>
            )}
        </form>
    );
}

/** 1 ocenę końcową, 2 oceny końcowe, 5 ocen końcowych */
function gradesWord(count: number): string {
    if (count === 1) {
        return 'ocenę końcową';
    }
    const lastDigit = count % 10;
    const lastTwo = count % 100;
    return lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14) ? 'oceny końcowe' : 'ocen końcowych';
}

interface GradeInputProps {
    row: FinalGradeSheetRow;
    value: GradeValue | null | undefined;
    text: string;
    numeric: boolean;
    inputRef: (el: HTMLInputElement | null) => void;
    onChange: (text: string) => void;
    onKeyDown: (event: KeyboardEvent<HTMLInputElement>) => void;
}

function GradeInput({ row, value, text, numeric, inputRef, onChange, onKeyDown }: GradeInputProps) {
    const differsFromProposal = value && row.proposedGradeValueId && value.id !== row.proposedGradeValueId;
    return (
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <input
                ref={inputRef}
                value={text}
                onChange={(e) => onChange(e.currentTarget.value)}
                onKeyDown={onKeyDown}
                aria-label={`Ocena końcowa: ${row.studentFirstName} ${row.studentLastName}`}
                aria-invalid={value === null}
                autoComplete="off"
                inputMode={numeric ? 'decimal' : 'text'}
                className={`w-16 rounded-sm border px-2 py-1 text-[15px] outline-none focus:ring-2 ${
                    value === null
                        ? 'border-red-700 focus:ring-red-700/20'
                        : 'border-line focus:border-navy focus:ring-navy/20'
                }`}
            />
            {value && value.label !== text.trim() && (
                <span className={`text-sm font-semibold ${value.passing ? '' : 'text-red-700'}`}>{value.label}</span>
            )}
            {row.openAttempt && row.openAttempt !== 'FIRST' && (
                <span className="text-xs text-muted">{ATTEMPT_LABELS[row.openAttempt].toLowerCase()}</span>
            )}
            {differsFromProposal && <span className="text-xs text-amber-700">inna niż propozycja</span>}
        </div>
    );
}
