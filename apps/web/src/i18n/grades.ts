import type { GradeValue, GradingScale } from '../api/types';

const PASS = ['z', 'zal', 'zaliczone', '+'];
const FAIL = ['n', 'nz', 'nzal', 'niezaliczone', '-'];

/**
 * Szybki wpis oceny z klawiatury:
 * "4" → 4,0 · "45" lub "4.5" → 4,5 · "z" → zal · "n" → nzal.
 * Zwraca undefined dla pustego pola i null dla wpisu, którego nie ma w skali.
 */
export function parseGrade(input: string, scale: GradingScale): GradeValue | null | undefined {
    const text = input.trim().toLowerCase().replace('.', ',');
    if (!text) {
        return undefined;
    }
    const byLabel = (label: string) => scale.values.find((v) => v.label.toLowerCase() === label) ?? null;

    if (scale.type === 'PASS_FAIL') {
        if (PASS.includes(text)) return byLabel('zal');
        if (FAIL.includes(text)) return byLabel('nzal');
        return byLabel(text);
    }
    if (/^\d$/.test(text)) return byLabel(`${text},0`);
    if (/^\d\d$/.test(text)) return byLabel(`${text[0]},${text[1]}`);
    return byLabel(text);
}

/** Podpowiedź pod nagłówkiem kolumny. */
export function gradeHint(scale: GradingScale): string {
    return scale.type === 'PASS_FAIL' ? 'z = zal, n = nzal' : '2, 3, 35, 4, 45, 5';
}