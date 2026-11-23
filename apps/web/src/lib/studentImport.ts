/**
 * Wczytywanie listy studentów wklejonej z Excela lub z pliku CSV. Czysta logika bez Reacta,
 * dlatego ma własne testy jednostkowe. Serwer i tak sprawdza wszystko jeszcze raz.
 */

export type ImportField = 'firstName' | 'lastName' | 'albumNumber';

export const IMPORT_FIELDS: ImportField[] = ['firstName', 'lastName', 'albumNumber'];

export const FIELD_LABELS: Record<ImportField, string> = {
    firstName: 'Imię',
    lastName: 'Nazwisko',
    albumNumber: 'Numer albumu',
};

/** Domena adresów studentów – ta sama co app.student-email-domain na serwerze. */
export const STUDENT_EMAIL_DOMAIN = 'tokstudiow.pl';

export interface ImportRow {
    firstName: string;
    lastName: string;
    albumNumber: string;
}

export interface CheckedRow extends ImportRow {
    /** adres uczelniany (login), który utworzy serwer */
    email: string;
    errors: Partial<Record<ImportField, string>>;
}

export interface ParsedTable {
    rows: ImportRow[];
    /** pierwszy wiersz był nagłówkiem i wyznaczył kolejność kolumn */
    headerDetected: boolean;
    delimiter: '\t' | ';' | ',';
}

/** Tyle osób przyjmuje serwer w jednym imporcie. */
export const MAX_IMPORT_ROWS = 300;

// nazwy kolumn rozpoznawane w nagłówku (małe litery, bez polskich znaków i odstępów)
const HEADER_NAMES: Record<string, ImportField> = {
    imie: 'firstName',
    firstname: 'firstName',
    nazwisko: 'lastName',
    lastname: 'lastName',
    surname: 'lastName',
    album: 'albumNumber',
    nralbumu: 'albumNumber',
    numeralbumu: 'albumNumber',
    albumnumber: 'albumNumber',
    indeks: 'albumNumber',
    nrindeksu: 'albumNumber',
};

const ALBUM = /^[0-9A-Za-z/-]{1,20}$/;

/** Małe litery bez polskich znaków i wszystkiego poza a–z. */
function asciiLetters(text: string): string {
    return text
        .toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/ł/g, 'l')
        .replace(/[^a-z]/g, '');
}

function normalizeHeader(cell: string): string {
    return asciiLetters(cell);
}

/**
 * Adres uczelniany studenta, tak jak tworzy go serwer (StudentEmailGenerator):
 * pierwsza litera imienia i nazwiska bez polskich znaków + numer albumu („/” → „-”) + domena.
 * Anna Nowak, 123456 → an123456@tokstudiow.pl
 */
export function studentEmail(
    firstName: string,
    lastName: string,
    albumNumber: string,
    domain: string = STUDENT_EMAIL_DOMAIN,
): string {
    const album = albumNumber
        .trim()
        .toLowerCase()
        .replace(/\//g, '-')
        .replace(/[^a-z0-9-]/g, '');
    return `${asciiLetters(firstName).charAt(0)}${asciiLetters(lastName).charAt(0)}${album}@${domain}`;
}

/** Tabulator (wklejka z Excela), a jeśli go nie ma – częstszy z ; i , w pierwszym wierszu. */
export function detectDelimiter(firstLine: string): ParsedTable['delimiter'] {
    if (firstLine.includes('\t')) {
        return '\t';
    }
    const semicolons = firstLine.split(';').length - 1;
    const commas = firstLine.split(',').length - 1;
    return semicolons >= commas && semicolons > 0 ? ';' : ',';
}

/** Jeden wiersz CSV z obsługą pól w cudzysłowach ("Kowalska, Anna") i podwójnych cudzysłowów. */
export function splitLine(line: string, delimiter: string): string[] {
    const cells: string[] = [];
    let cell = '';
    let quoted = false;
    for (let i = 0; i < line.length; i++) {
        const ch = line[i];
        if (quoted) {
            if (ch === '"' && line[i + 1] === '"') {
                cell += '"';
                i++;
            } else if (ch === '"') {
                quoted = false;
            } else {
                cell += ch;
            }
        } else if (ch === '"' && cell.trim() === '') {
            quoted = true;
            cell = '';
        } else if (ch === delimiter) {
            cells.push(cell.trim());
            cell = '';
        } else {
            cell += ch;
        }
    }
    cells.push(cell.trim());
    return cells;
}

/**
 * Tekst → wiersze. Z nagłówkiem kolumny mogą być w dowolnej kolejności;
 * bez nagłówka obowiązuje kolejność: imię, nazwisko, numer albumu. Inne kolumny (np. e-mail)
 * są pomijane – adres tworzy system.
 */
export function parseStudentTable(text: string): ParsedTable {
    const lines = text
        .replace(/^﻿/, '')
        .split(/\r\n|\n|\r/)
        .filter((line) => line.trim() !== '');
    if (lines.length === 0) {
        return { rows: [], headerDetected: false, delimiter: ',' };
    }

    const delimiter = detectDelimiter(lines[0]);
    const table = lines.map((line) => splitLine(line, delimiter));

    const headerFields = table[0].map((cell) => HEADER_NAMES[normalizeHeader(cell)]);
    const headerDetected = headerFields.filter(Boolean).length >= 2;
    const columns: (ImportField | undefined)[] = headerDetected ? headerFields : IMPORT_FIELDS;
    const body = headerDetected ? table.slice(1) : table;

    const rows = body.map((cells) => {
        const row: ImportRow = { firstName: '', lastName: '', albumNumber: '' };
        columns.forEach((field, index) => {
            if (field && cells[index] !== undefined) {
                row[field] = cells[index];
            }
        });
        return row;
    });
    return { rows, headerDetected, delimiter };
}

/**
 * Zawartość pliku CSV jako tekst. „CSV UTF-8” z Excela czyta się wprost; zwykłe „CSV”
 * z polskiego Excela jest w Windows-1250, więc gdy bajty nie są poprawnym UTF-8, próbujemy tego kodowania.
 */
export function decodeCsv(bytes: ArrayBuffer): string {
    try {
        return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    } catch {
        return new TextDecoder('windows-1250').decode(bytes);
    }
}

/**
 * Sprawdza każdy wiersz i powtórzenia w obrębie listy. Adres powstaje z numeru albumu,
 * więc jego powtórzenie (np. „12/2026” i „12-2026”) też zgłaszane jest przy albumie.
 */
export function checkRows(rows: ImportRow[]): CheckedRow[] {
    const withEmail = rows.map((row) => ({ ...row, email: studentEmail(row.firstName, row.lastName, row.albumNumber) }));
    const count = (values: string[]) => {
        const map = new Map<string, number>();
        values.filter(Boolean).forEach((v) => map.set(v, (map.get(v) ?? 0) + 1));
        return map;
    };
    const albums = count(withEmail.map((r) => r.albumNumber.trim()));
    const emails = count(withEmail.map((r) => r.email));

    return withEmail.map((row) => {
        const errors: CheckedRow['errors'] = {};
        const album = row.albumNumber.trim();

        if (!row.firstName.trim()) errors.firstName = 'Brak imienia';
        else if (row.firstName.trim().length > 100) errors.firstName = 'Za długie (maks. 100 znaków)';
        if (!row.lastName.trim()) errors.lastName = 'Brak nazwiska';
        else if (row.lastName.trim().length > 100) errors.lastName = 'Za długie (maks. 100 znaków)';

        if (!album) errors.albumNumber = 'Brak numeru albumu';
        else if (!ALBUM.test(album)) errors.albumNumber = 'Tylko cyfry, litery, „/” i „-”, maks. 20 znaków';
        else if ((albums.get(album) ?? 0) > 1) errors.albumNumber = 'Powtarza się na liście';
        else if ((emails.get(row.email) ?? 0) > 1) errors.albumNumber = 'Ten sam login co w innym wierszu';

        return { ...row, errors };
    });
}

export function hasErrors(row: CheckedRow): boolean {
    return Object.keys(row.errors).length > 0;
}

/**
 * Plik CSV z hasłami startowymi do przekazania studentom. Średnik i znacznik BOM,
 * żeby polski Excel otworzył go od razu z poprawnymi kolumnami i znakami.
 */
export function credentialsCsv(
    rows: { firstName: string; lastName: string; email: string; albumNumber: string; initialPassword: string }[],
): string {
    const quote = (value: string) => (/[;"\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value);
    const lines = [
        ['Nazwisko', 'Imię', 'Numer albumu', 'E-mail (login)', 'Hasło startowe'],
        ...rows.map((r) => [r.lastName, r.firstName, r.albumNumber, r.email, r.initialPassword]),
    ];
    return '﻿' + lines.map((line) => line.map(quote).join(';')).join('\r\n') + '\r\n';
}
