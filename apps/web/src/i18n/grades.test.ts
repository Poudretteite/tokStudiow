import { describe, expect, it } from 'vitest';
import type { GradingScale } from '../api/types';
import { parseGrade } from './grades';

const numeric: GradingScale = {
    id: 'n',
    name: 'Skala 2,0–5,0',
    type: 'NUMERIC',
    values: ['2,0', '3,0', '3,5', '4,0', '4,5', '5,0'].map((label) => ({
        id: label,
        label,
        numericValue: Number(label.replace(',', '.')),
        passing: label !== '2,0',
    })),
};

const passFail: GradingScale = {
    id: 'z',
    name: 'Zaliczenie',
    type: 'PASS_FAIL',
    values: [
        { id: 'zal', label: 'zal', numericValue: null, passing: true },
        { id: 'nzal', label: 'nzal', numericValue: null, passing: false },
    ],
};

describe('parseGrade', () => {
    it.each([
        ['4', '4,0'],
        ['45', '4,5'],
        ['4.5', '4,5'],
        ['4,5', '4,5'],
        [' 3 ', '3,0'],
        ['2', '2,0'],
    ])('reads %j as %s', (input, label) => {
        expect(parseGrade(input, numeric)?.label).toBe(label);
    });

    it('returns undefined for an empty field and null for a value outside the scale', () => {
        expect(parseGrade('', numeric)).toBeUndefined();
        expect(parseGrade('7', numeric)).toBeNull();
        expect(parseGrade('25', numeric)).toBeNull();
        expect(parseGrade('abc', numeric)).toBeNull();
    });

    it('understands short pass/fail entries', () => {
        expect(parseGrade('z', passFail)?.label).toBe('zal');
        expect(parseGrade('n', passFail)?.label).toBe('nzal');
        expect(parseGrade('NZAL', passFail)?.label).toBe('nzal');
        expect(parseGrade('4', passFail)).toBeNull();
    });
});
