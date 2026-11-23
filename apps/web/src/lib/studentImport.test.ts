import { describe, expect, it } from 'vitest';
import {
    checkRows,
    credentialsCsv,
    decodeCsv,
    detectDelimiter,
    hasErrors,
    parseStudentTable,
    splitLine,
    studentEmail,
} from './studentImport';

describe('parseStudentTable', () => {
    it('reads a paste from Excel (tabs) without header in the default column order', () => {
        const { rows, headerDetected, delimiter } = parseStudentTable('Anna\tNowak\t123456\nJan\tKowalski\t123457\n');
        expect(delimiter).toBe('\t');
        expect(headerDetected).toBe(false);
        expect(rows).toEqual([
            { firstName: 'Anna', lastName: 'Nowak', albumNumber: '123456' },
            { firstName: 'Jan', lastName: 'Kowalski', albumNumber: '123457' },
        ]);
    });

    it('maps columns by Polish header names in any order and skips an e-mail column', () => {
        const { rows, headerDetected } = parseStudentTable(
            'Nr albumu;Nazwisko;Adres e-mail;Imię\n123456;Nowak;anna@gmail.com;Anna',
        );
        expect(headerDetected).toBe(true);
        expect(rows[0]).toEqual({ firstName: 'Anna', lastName: 'Nowak', albumNumber: '123456' });
    });

    it('handles a UTF-8 BOM, Windows line endings and blank lines from a CSV file', () => {
        const { rows } = parseStudentTable('\uFEFFImię,Nazwisko,Album\r\n\r\nŁucja,Żak,9/2026\r\n');
        expect(rows).toEqual([{ firstName: 'Łucja', lastName: 'Żak', albumNumber: '9/2026' }]);
    });

    it('keeps quoted fields with delimiters and doubled quotes', () => {
        expect(splitLine('"Anna, Maria";"Nowak ""Mała""";1', ';')).toEqual(['Anna, Maria', 'Nowak "Mała"', '1']);
    });

    it('returns no rows for empty input', () => {
        expect(parseStudentTable('  \n\n').rows).toEqual([]);
    });
});

describe('studentEmail', () => {
    it('joins initials and album number with the university domain', () => {
        expect(studentEmail('Anna', 'Nowak', '123456')).toBe('an123456@tokstudiow.pl');
    });

    it('drops Polish letters, spaces and turns "/" into "-" like the server', () => {
        expect(studentEmail(' Łucja ', 'Żak', '12/2026')).toBe('lz12-2026@tokstudiow.pl');
        expect(studentEmail('Ewa', 'Ślusarczyk-Kot', '2001')).toBe('es2001@tokstudiow.pl');
        expect(studentEmail('Zofia', 'Ćwik', 'AB-12')).toBe('zcab-12@tokstudiow.pl');
    });
});

describe('detectDelimiter', () => {
    it('prefers tab, then the more frequent of semicolon and comma', () => {
        expect(detectDelimiter('a\tb;c')).toBe('\t');
        expect(detectDelimiter('a;b;c,d')).toBe(';');
        expect(detectDelimiter('a,b,c')).toBe(',');
        expect(detectDelimiter('jedna kolumna')).toBe(',');
    });
});

describe('checkRows', () => {
    const ok = { firstName: 'Anna', lastName: 'Nowak', albumNumber: '123456' };

    it('accepts a complete row and shows the generated login', () => {
        const [row] = checkRows([ok]);
        expect(hasErrors(row)).toBe(false);
        expect(row.email).toBe('an123456@tokstudiow.pl');
    });

    it('reports missing and malformed fields', () => {
        const [row] = checkRows([{ firstName: '', lastName: ' ', albumNumber: '12 34' }]);
        expect(Object.keys(row.errors).sort()).toEqual(['albumNumber', 'firstName', 'lastName']);
    });

    it('flags duplicate album numbers within the list', () => {
        const rows = checkRows([ok, { ...ok, firstName: 'Jan' }, { ...ok, albumNumber: '999' }]);
        expect(rows[0].errors.albumNumber).toBe('Powtarza się na liście');
        expect(rows[1].errors.albumNumber).toBe('Powtarza się na liście');
        expect(rows[2].errors.albumNumber).toBeUndefined();
    });

    it('flags different album numbers that would give the same login', () => {
        const rows = checkRows([
            { ...ok, albumNumber: '12/2026' },
            { ...ok, albumNumber: '12-2026' },
        ]);
        expect(rows[0].errors.albumNumber).toBe('Ten sam login co w innym wierszu');
        expect(rows[1].errors.albumNumber).toBe('Ten sam login co w innym wierszu');
    });
});

describe('credentialsCsv', () => {
    it('writes a semicolon CSV with BOM that Polish Excel opens directly', () => {
        const csv = credentialsCsv([
            { firstName: 'Anna', lastName: 'Nowak', email: 'anna@uni.pl', albumNumber: '1', initialPassword: 'Ab;c"d' },
        ]);
        expect(csv.startsWith('﻿Nazwisko;Imię;')).toBe(true);
        expect(csv).toContain('Nowak;Anna;1;anna@uni.pl;"Ab;c""d"\r\n');
    });
});

describe('decodeCsv', () => {
    it('reads UTF-8 and falls back to Windows-1250 from Polish Excel', () => {
        const utf8 = new TextEncoder().encode('Łucja;Żak').buffer;
        // "Łucja;Żak" w Windows-1250: Ł = 0xA3, Ż = 0xAF
        const cp1250 = new Uint8Array([0xa3, 0x75, 0x63, 0x6a, 0x61, 0x3b, 0xaf, 0x61, 0x6b]).buffer;
        expect(decodeCsv(utf8)).toBe('Łucja;Żak');
        expect(decodeCsv(cp1250)).toBe('Łucja;Żak');
    });
});
