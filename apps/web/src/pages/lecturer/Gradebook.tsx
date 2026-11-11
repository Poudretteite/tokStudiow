import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
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

export function Gradebook({ group }: { group: ClassGroup }) {
    const groupId = encodeURIComponent(group.id);
    const itemsKey = ['groups', groupId, 'items'];
    const gradesKey = ['lecturer', 'groups', groupId, 'grades'];

    const roster = useQuery({
        queryKey: ['groups', groupId, 'students'],
        queryFn: () => api<RosterEntry[]>(`/api/groups/${groupId}/students`),
    });
    const items = useQuery({
        queryKey: itemsKey,
        queryFn: () => api<AssessmentItem[]>(`/api/groups/${groupId}/assessment-items`),
    });
    const grades = useQuery({
        queryKey: gradesKey,
        queryFn: () => api<PartialGrade[]>(`/api/lecturer/groups/${groupId}/partial-grades`),
    });
    const scales = useQuery({ queryKey: ['grading-scales'], queryFn: () => api<GradingScale[]>('/api/grading-scales') });
    const componentScale = scales.data?.find((s) => s.id === group.gradingScaleId);

    return (
        <div className="flex flex-col gap-6">
            <QueryState query={roster}>
                {(students) => (
                    <QueryState query={items}>
                        {(itemList) => (
                            <QueryState query={grades}>
                                {(gradeList) =>
                                    scales.data ? (
                                        <GradeTable
                                            groupId={groupId}
                                            students={students}
                                            items={itemList}
                                            grades={gradeList}
                                            scales={scales.data}
                                            gradesKey={gradesKey}
                                        />
                                    ) : null
                                }
                            </QueryState>
                        )}
                    </QueryState>
                )}
            </QueryState>

            <QueryState query={items}>
                {(itemList) =>
                    componentScale ? (
                        <AddItemForm
                            groupId={groupId}
                            items={itemList}
                            queryKey={itemsKey}
                            defaultType={componentScale.type}
                        />
                    ) : null
                }
            </QueryState>
        </div>
    );
}

// tabela: wpisywanie całej kolumny i korekta pojedynczej oceny

interface GradeTableProps {
    groupId: string;
    students: RosterEntry[];
    items: AssessmentItem[];
    grades: PartialGrade[];
    scales: GradingScale[];
    gradesKey: string[];
}

interface CorrectionTarget {
    student: RosterEntry;
    item: AssessmentItem;
    grade: PartialGrade;
}

function GradeTable({ groupId, students, items, grades, scales, gradesKey }: GradeTableProps) {
    const queryClient = useQueryClient();
    const [activeItemId, setActiveItemId] = useState<string | null>(null);
    const [drafts, setDrafts] = useState<Record<string, string>>({});
    const [notice, setNotice] = useState<string | null>(null);
    const [correction, setCorrection] = useState<CorrectionTarget | null>(null);
    const inputs = useRef<(HTMLInputElement | null)[]>([]);
    const saveButton = useRef<HTMLButtonElement>(null);

    // Każdy element ma własną skalę: ocena 2,0–5,0 albo zal/nzal
    const scaleOf = (item: AssessmentItem) => scales.find((s) => s.id === item.gradingScaleId)!;
    const activeItem = items.find((i) => i.id === activeItemId);
    const activeScale = activeItem ? scaleOf(activeItem) : undefined;

    const parsed = useMemo(
        () =>
            students.map((s) => ({
                student: s,
                value: activeScale ? parseGrade(drafts[s.enrollmentId] ?? '', activeScale) : undefined,
            })),
        [students, drafts, activeScale],
    );
    const toSave = parsed.filter((p) => p.value);
    const hasInvalid = parsed.some((p) => p.value === null);

    const save = useMutation({
        mutationFn: () =>
            api(`/api/lecturer/groups/${groupId}/partial-grades/batch`, {
                method: 'POST',
                body: JSON.stringify({
                    assessmentItemId: activeItemId,
                    entries: toSave.map((p) => ({ enrollmentId: p.student.enrollmentId, gradeValueId: p.value!.id })),
                }),
            }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey: gradesKey });
            setNotice(`Zapisano oceny: ${toSave.length}.`);
            setActiveItemId(null);
            setDrafts({});
        },
    });

    if (students.length === 0) {
        return <p className="text-muted">Do grupy nie zapisano jeszcze studentów.</p>;
    }
    if (items.length === 0) {
        return <p className="text-muted">Dodaj element zaliczenia, aby wystawiać oceny.</p>;
    }

    function start(itemId: string) {
        setActiveItemId(itemId);
        setDrafts({});
        setNotice(null);
        save.reset();
        requestAnimationFrame(() => inputs.current[0]?.focus());
    }

    function cancel() {
        setActiveItemId(null);
        setDrafts({});
        save.reset();
    }

    function fillEmpty(label: string) {
        setDrafts((current) => {
            const next = { ...current };
            for (const s of students) {
                if (!next[s.enrollmentId]?.trim()) {
                    next[s.enrollmentId] = label;
                }
            }
            return next;
        });
    }

    // Enter / ↓ – następna osoba, ↑ – poprzednia; Enter w ostatnim wierszu przechodzi do przycisku zapisu
    function handleKeyDown(event: KeyboardEvent<HTMLInputElement>, index: number) {
        if (event.key === 'Enter' || event.key === 'ArrowDown') {
            event.preventDefault();
            const next = inputs.current[index + 1];
            if (next) {
                next.focus();
            } else if (event.key === 'Enter') {
                saveButton.current?.focus();
            }
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            inputs.current[index - 1]?.focus();
        }
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        save.mutate();
    }

    const attemptsFor = (enrollmentId: string, itemId: string) =>
        grades
            .filter((g) => g.enrollmentId === enrollmentId && g.assessmentItemId === itemId)
            .sort((a, b) => a.attempt - b.attempt);

    return (
        <>
            <form onSubmit={handleSubmit}>
                {notice && (
                    <div className="mb-4">
                        <Alert variant="success">{notice}</Alert>
                    </div>
                )}

                <Panel title="Oceny cząstkowe" flush>
                    <Table minWidth={240 + items.length * 150}>
                        <thead>
                        <tr>
                            <Th>Student</Th>
                            {items.map((item) => (
                                <Th key={item.id}>
                                    <div>{item.name}</div>
                                    <div className="text-xs font-normal text-muted">
                                        {item.gradingType === 'PASS_FAIL' ? 'zal/nzal' : `ocena, waga ${item.weightPercent}%`}
                                    </div>
                                    {item.id === activeItemId ? (
                                        <div className="mt-1 text-xs font-normal text-navy">{gradeHint(scaleOf(item))}</div>
                                    ) : (
                                        <button
                                            type="button"
                                            onClick={() => start(item.id)}
                                            disabled={activeItemId !== null}
                                            className="mt-1 text-xs font-semibold text-navy underline underline-offset-2 disabled:cursor-not-allowed disabled:text-muted disabled:no-underline"
                                        >
                                            Wpisz oceny
                                        </button>
                                    )}
                                </Th>
                            ))}
                        </tr>
                        </thead>
                        <tbody>
                        {students.map((student, index) => (
                            <Tr key={student.enrollmentId}>
                                <Td>
                                    {student.lastName} {student.firstName}
                                    <div className="text-xs text-muted">{student.albumNumber}</div>
                                </Td>
                                {items.map((item) => {
                                    const attempts = attemptsFor(student.enrollmentId, item.id);
                                    const current = attempts.at(-1);

                                    // Kolumna w trybie przeglądania: ocenę można kliknąć, żeby skorygować błędny wpis
                                    if (item.id !== activeItemId) {
                                        return (
                                            <Td key={item.id}>
                                                {current ? (
                                                    <button
                                                        type="button"
                                                        onClick={() => setCorrection({ student, item, grade: current })}
                                                        disabled={activeItemId !== null}
                                                        aria-label={`Korekta oceny ${current.gradeLabel}: ${student.firstName} ${student.lastName}, ${item.name}`}
                                                        title="Kliknij, aby skorygować błędnie wpisaną ocenę"
                                                        className="cursor-pointer rounded-sm px-1 underline decoration-line decoration-dotted underline-offset-4 hover:bg-page hover:decoration-navy focus-visible:outline-2 focus-visible:outline-navy disabled:cursor-default disabled:no-underline disabled:hover:bg-transparent"
                                                    >
                              <span className={`font-semibold ${current.passing ? '' : 'text-red-700'}`}>
                                {current.gradeLabel}
                              </span>
                                                        {attempts.length > 1 && (
                                                            <span className="ml-1 text-xs text-muted">({attempts.length} próby)</span>
                                                        )}
                                                    </button>
                                                ) : (
                                                    <span className="text-muted">–</span>
                                                )}
                                            </Td>
                                        );
                                    }

                                    // Kolumna w trybie wpisywania
                                    const value = parsed[index].value;
                                    return (
                                        <Td key={item.id}>
                                            <div className="flex items-center gap-2">
                                                <input
                                                    ref={(el) => {
                                                        inputs.current[index] = el;
                                                    }}
                                                    value={drafts[student.enrollmentId] ?? ''}
                                                    onChange={(e) => {
                                                        const text = e.currentTarget.value;
                                                        setDrafts((d) => ({ ...d, [student.enrollmentId]: text }));
                                                    }}
                                                    onKeyDown={(e) => handleKeyDown(e, index)}
                                                    aria-label={`Ocena: ${student.firstName} ${student.lastName}`}
                                                    aria-invalid={value === null}
                                                    autoComplete="off"
                                                    inputMode={activeScale?.type === 'NUMERIC' ? 'decimal' : 'text'}
                                                    className={`w-16 rounded-sm border px-2 py-1 text-[15px] outline-none focus:ring-2 ${
                                                        value === null
                                                            ? 'border-red-700 focus:ring-red-700/20'
                                                            : 'border-line focus:border-navy focus:ring-navy/20'
                                                    }`}
                                                />
                                                {value && <span className="text-sm font-semibold">{value.label}</span>}
                                                {current && <span className="text-xs text-muted">było {current.gradeLabel}</span>}
                                            </div>
                                        </Td>
                                    );
                                })}
                            </Tr>
                        ))}
                        </tbody>
                    </Table>

                    {!activeItem && (
                        <p className="border-t border-line px-4 py-2 text-sm text-muted">
                            Pomyłka przy wpisywaniu? Kliknij ocenę, aby ją skorygować. Ocenę z poprawy zaliczenia wpisz przez „Wpisz
                            oceny”.
                        </p>
                    )}

                    {activeItem && activeScale && (
                        <div className="sticky bottom-0 flex flex-wrap items-center gap-3 border-t border-line bg-white px-4 py-3">
                            <Button
                                ref={saveButton}
                                type="submit"
                                loading={save.isPending}
                                disabled={toSave.length === 0 || hasInvalid}
                            >
                                Zapisz oceny ({toSave.length})
                            </Button>
                            <Button type="button" variant="secondary" onClick={cancel}>
                                Anuluj
                            </Button>
                            <label className="flex items-center gap-2 text-sm">
                                Uzupełnij puste:
                                <select
                                    value=""
                                    onChange={(e) => e.currentTarget.value && fillEmpty(e.currentTarget.value)}
                                    className="rounded-sm border border-line bg-white px-2 py-1"
                                >
                                    <option value="">wybierz</option>
                                    {activeScale.values.map((v) => (
                                        <option key={v.id} value={v.label}>
                                            {v.label}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <p className="w-full text-sm text-muted">
                                {activeItem.name}: wpisz ocenę i naciśnij Enter, aby przejść do następnej osoby. Puste pola zostaną
                                pominięte. Jeśli osoba ma już ocenę, nowa zostanie zapisana jako kolejna próba (poprawa).
                            </p>
                            {hasInvalid && <Alert>Popraw oceny zaznaczone na czerwono.</Alert>}
                            {save.isError && <Alert>{errorMessage(save.error)}</Alert>}
                        </div>
                    )}
                </Panel>
            </form>

            {correction && (
                <CorrectionDialog
                    groupId={groupId}
                    target={correction}
                    scale={scaleOf(correction.item)}
                    gradesKey={gradesKey}
                    onClose={() => setCorrection(null)}
                    onCorrected={(label) => setNotice(`Skorygowano ocenę na ${label}.`)}
                />
            )}
        </>
    );
}

// korekta błędnie wpisanej oceny

function CorrectionDialog({
                              groupId,
                              target,
                              scale,
                              gradesKey,
                              onClose,
                              onCorrected,
                          }: {
    groupId: string;
    target: CorrectionTarget;
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
            api(`/api/lecturer/groups/${groupId}/partial-grades/${encodeURIComponent(target.grade.id)}`, {
                method: 'PATCH',
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
        <Dialog open onClose={onClose} title="Korekta błędnie wpisanej oceny">
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
                        inputMode={scale.type === 'NUMERIC' ? 'decimal' : 'text'}
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
                    label="Powód korekty"
                    required
                    maxLength={500}
                    placeholder="np. pomyłka przy wpisywaniu"
                    value={reason}
                    onChange={(e) => setReason(e.currentTarget.value)}
                />

                <p className="text-sm text-muted">
                    Korekta zastępuje błędną ocenę: student zobaczy tylko prawidłową wartość, bez wpisu w historii ocen. Zmiana
                    z powodem trafia wyłącznie do dziennika audytu, widocznego dla administratora. Jeśli student poprawiał
                    zaliczenie, nie używaj korekty: wpisz nową ocenę przez „Wpisz oceny”, a zostanie zapisana jako kolejna próba.
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

// dodawanie elementu zaliczenia

type GradingType = GradingScale['type'];

function AddItemForm({
                         groupId,
                         items,
                         queryKey,
                         defaultType,
                     }: {
    groupId: string;
    items: AssessmentItem[];
    queryKey: string[];
    defaultType: GradingType;
}) {
    const queryClient = useQueryClient();
    const [name, setName] = useState('');
    const [gradingType, setGradingType] = useState<GradingType>(defaultType);
    const [weight, setWeight] = useState('');
    const [plannedDate, setPlannedDate] = useState('');
    const usedWeight = items
        .filter((item) => item.gradingType === 'NUMERIC')
        .reduce((sum, item) => sum + Number(item.weightPercent), 0);
    const graded = gradingType === 'NUMERIC';

    const add = useMutation({
        mutationFn: () =>
            api(`/api/lecturer/groups/${groupId}/assessment-items`, {
                method: 'POST',
                body: JSON.stringify({
                    name,
                    gradingType,
                    weightPercent: graded ? Number(weight) : null,
                    plannedDate: plannedDate || null,
                }),
            }),
        onSuccess: async () => {
            await queryClient.invalidateQueries({ queryKey });
            setName('');
            setWeight('');
            setPlannedDate('');
        },
    });

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        add.mutate();
    }

    return (
        <Panel
            title="Dodaj element zaliczenia"
            aside={<span className="text-sm">Suma wag ocen: {usedWeight}% ze 100%</span>}
        >
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <fieldset>
                    <legend className="mb-1 text-sm font-semibold">Sposób oceniania</legend>
                    <div className="flex flex-wrap gap-x-6 gap-y-1">
                        <label className="flex items-center gap-2">
                            <input
                                type="radio"
                                name="gradingType"
                                checked={graded}
                                onChange={() => setGradingType('NUMERIC')}
                                className="accent-navy"
                            />
                            Ocena (2,0–5,0), wlicza się do średniej
                        </label>
                        <label className="flex items-center gap-2">
                            <input
                                type="radio"
                                name="gradingType"
                                checked={!graded}
                                onChange={() => setGradingType('PASS_FAIL')}
                                className="accent-navy"
                            />
                            Zaliczenie (zal/nzal), warunek zaliczenia
                        </label>
                    </div>
                </fieldset>

                <div className={`grid gap-4 ${graded ? 'sm:grid-cols-[2fr_1fr_1fr]' : 'sm:grid-cols-[2fr_1fr]'}`}>
                    <TextField label="Nazwa" required maxLength={100} value={name} onChange={(e) => setName(e.currentTarget.value)} />
                    {graded && (
                        <TextField
                            label="Waga (%)"
                            type="number"
                            required
                            min={0.01}
                            max={100 - usedWeight}
                            step={0.01}
                            value={weight}
                            onChange={(e) => setWeight(e.currentTarget.value)}
                        />
                    )}
                    <TextField
                        label="Planowany termin"
                        type="date"
                        value={plannedDate}
                        onChange={(e) => setPlannedDate(e.currentTarget.value)}
                    />
                </div>
                {add.isError && <Alert>{errorMessage(add.error)}</Alert>}
                <div>
                    <Button type="submit" loading={add.isPending} disabled={graded && usedWeight >= 100}>
                        Dodaj element
                    </Button>
                </div>
            </form>
        </Panel>
    );
}
