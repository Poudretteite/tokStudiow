import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { ApiError, api } from '../../api/client';
import type { FieldOfStudy, ImportedStudent } from '../../api/types';
import { Panel } from '../../components/Panel';
import { Alert } from '../../components/ui/Alert';
import { Button } from '../../components/ui/Button';
import { Select } from '../../components/ui/Select';
import { Table, Td, Th, Tr } from '../../components/ui/Table';
import { TextField } from '../../components/ui/TextField';
import { errorMessage, messageText } from '../../i18n/errors';
import {
    FIELD_LABELS,
    IMPORT_FIELDS,
    MAX_IMPORT_ROWS,
    checkRows,
    credentialsCsv,
    decodeCsv,
    hasErrors,
    parseStudentTable,
    STUDENT_EMAIL_DOMAIN,
    type ImportField,
} from '../../lib/studentImport';

const MAX_FILE_BYTES = 1024 * 1024;

/**
 * Hurtowe zakładanie kont studentów: wklejenie kolumn z Excela albo plik CSV, podgląd
 * z błędami w każdym wierszu i jeden zapis. Hasła startowe pokazują się raz, z plikiem do pobrania.
 */
export function StudentImportPanel({
    fields,
    defaultStartYear,
    invalidateKeys,
}: {
    fields: FieldOfStudy[];
    defaultStartYear: number;
    invalidateKeys: string[][];
}) {
    const queryClient = useQueryClient();
    const fileInput = useRef<HTMLInputElement>(null);
    const [text, setText] = useState('');
    const [fileName, setFileName] = useState<string | null>(null);
    const [fileError, setFileError] = useState<string | null>(null);
    const [chosenFieldId, setFieldId] = useState('');
    const [startYear, setStartYear] = useState(String(defaultStartYear));
    const [semester, setSemester] = useState('1');
    const [imported, setImported] = useState<ImportedStudent[] | null>(null);
    const fieldId = chosenFieldId || fields[0]?.id || '';

    const parsed = parseStudentTable(text);
    const rows = checkRows(parsed.rows);
    const invalid = rows.filter(hasErrors).length;
    const tooMany = rows.length > MAX_IMPORT_ROWS;

    const save = useMutation({
        mutationFn: () =>
            api<ImportedStudent[]>('/api/dean-office/students/import', {
                method: 'POST',
                body: JSON.stringify({
                    fieldOfStudyId: fieldId,
                    startYear: Number(startYear),
                    currentSemester: Number(semester),
                    // adres nie jest wysyłany: serwer tworzy go sam z inicjałów i numeru albumu
                    students: rows.map(({ firstName, lastName, albumNumber }) => ({ firstName, lastName, albumNumber })),
                }),
            }),
        onSuccess: async (result) => {
            await Promise.all(invalidateKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
            setImported(result);
            setText('');
            setFileName(null);
        },
    });

    // błędy z serwera (np. numer albumu lub login już w systemie) pokazujemy przy właściwym wierszu
    const serverErrors = new Map<string, string>();
    if (save.error instanceof ApiError) {
        for (const e of save.error.rows) {
            serverErrors.set(`${e.row - 1}:${e.field}`, messageText(e.message));
        }
    }

    async function handleFile(event: ChangeEvent<HTMLInputElement>) {
        const file = event.currentTarget.files?.[0];
        event.currentTarget.value = '';
        setFileError(null);
        if (!file) {
            return;
        }
        if (file.size > MAX_FILE_BYTES) {
            setFileError('Plik jest za duży (maks. 1 MB).');
            return;
        }
        setText(decodeCsv(await file.arrayBuffer()));
        setFileName(file.name);
        setImported(null);
        save.reset();
    }

    function handleSubmit(event: FormEvent) {
        event.preventDefault();
        setImported(null);
        save.mutate();
    }

    function downloadPasswords() {
        if (!imported) {
            return;
        }
        const url = URL.createObjectURL(new Blob([credentialsCsv(imported)], { type: 'text/csv;charset=utf-8' }));
        const link = document.createElement('a');
        link.href = url;
        link.download = `hasla-startowe-${new Date().toISOString().slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    }

    return (
        <Panel title="Dodaj studentów">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <div className="grid gap-4 sm:grid-cols-[2fr_1fr_1fr]">
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

                <div>
                    <label htmlFor="student-list" className="mb-1 block text-sm font-semibold">
                        Lista studentów
                    </label>
                    <textarea
                        id="student-list"
                        rows={6}
                        spellCheck={false}
                        value={text}
                        onChange={(e) => {
                            setText(e.currentTarget.value);
                            setFileName(null);
                            setImported(null);
                            save.reset();
                        }}
                        placeholder={'Wklej kolumny z Excela: imię, nazwisko, numer albumu\nAnna\tNowak\t123470'}
                        className="w-full rounded-sm border border-line bg-white px-3 py-2 font-mono text-sm outline-none focus:border-navy focus:ring-2 focus:ring-navy/20"
                    />
                    <div className="mt-2 flex flex-wrap items-center gap-3 text-sm">
                        <Button type="button" variant="secondary" onClick={() => fileInput.current?.click()}>
                            Wybierz plik CSV
                        </Button>
                        <input
                            ref={fileInput}
                            type="file"
                            accept=".csv,.txt,text/csv,text/plain"
                            className="hidden"
                            onChange={handleFile}
                        />
                        {fileName && <span>Wczytano: {fileName}</span>}
                        <span className="text-muted">
                            Kolumny rozpoznaję po nagłówku (Imię, Nazwisko, Nr albumu); bez nagłówka obowiązuje ta kolejność.
                            Separator: tabulator, średnik lub przecinek. E-mail (login) tworzy system: inicjały + numer albumu
                            + @{STUDENT_EMAIL_DOMAIN}.
                        </span>
                    </div>
                    {fileError && <p className="mt-1 text-sm text-red-700">{fileError}</p>}
                </div>

                {rows.length > 0 && (
                    <div className="border border-line">
                        <Table minWidth={720}>
                            <thead>
                            <tr>
                                <Th className="w-12">Lp.</Th>
                                {IMPORT_FIELDS.map((f) => (
                                    <Th key={f}>{FIELD_LABELS[f]}</Th>
                                ))}
                                <Th>E-mail (login)</Th>
                            </tr>
                            </thead>
                            <tbody>
                            {rows.slice(0, MAX_IMPORT_ROWS).map((row, index) => (
                                <Tr key={index}>
                                    <Td className="text-muted">{index + 1}</Td>
                                    {IMPORT_FIELDS.map((f: ImportField) => {
                                        const problem = row.errors[f] ?? serverErrors.get(`${index}:${f}`);
                                        return (
                                            <Td key={f} className={problem ? 'bg-red-50' : ''}>
                                                {row[f] || <span className="text-muted">–</span>}
                                                {problem && <div className="text-xs text-red-700">{problem}</div>}
                                            </Td>
                                        );
                                    })}
                                    <Td className="text-muted">{hasErrors(row) ? '–' : row.email}</Td>
                                </Tr>
                            ))}
                            </tbody>
                        </Table>
                    </div>
                )}

                {tooMany && (
                    <Alert>
                        Lista ma {rows.length} osób, a jednorazowo można dodać {MAX_IMPORT_ROWS}. Podziel ją na części.
                    </Alert>
                )}
                {save.isError && <Alert>{errorMessage(save.error)}</Alert>}

                <div className="flex flex-wrap items-center gap-3">
                    <Button
                        type="submit"
                        loading={save.isPending}
                        disabled={rows.length === 0 || invalid > 0 || tooMany || !fieldId}
                    >
                        Dodaj studentów ({rows.length})
                    </Button>
                    {invalid > 0 && (
                        <span className="text-sm text-red-700">Do poprawienia: {invalid} – nikt nie zostanie dodany, dopóki są błędy.</span>
                    )}
                    {save.isPending && rows.length > 50 && (
                        <span className="text-sm text-muted">Zakładanie kont może potrwać kilka sekund.</span>
                    )}
                </div>

                {imported && (
                    <div className="flex flex-col gap-3">
                        <Alert variant="success">
                            Dodano studentów: <strong>{imported.length}</strong>. Pobierz listę haseł startowych teraz – po
                            opuszczeniu strony nie da się jej wyświetlić ponownie.
                        </Alert>
                        <div>
                            <Button type="button" onClick={downloadPasswords}>
                                Pobierz listę haseł (CSV)
                            </Button>
                        </div>
                        <p className="text-sm text-muted">
                            Przekaż hasła studentom osobiście lub inną bezpieczną drogą, nigdy w tym samym mailu co login. Po
                            rozdaniu usuń plik z komputera.
                        </p>
                    </div>
                )}
            </form>
        </Panel>
    );
}
