import { describe, expect, it } from 'vitest';
import { isPasswordAcceptable, passwordRules } from './passwordPolicy';

const jan = { email: 'jk123456@tokstudiow.pl', firstName: 'Jan', lastName: 'Kowalski' };
const failed = (password: string, owner = jan) =>
    passwordRules(password, owner)
        .filter((rule) => !rule.ok)
        .map((rule) => rule.id);

describe('passwordRules (te same przypadki co PasswordPolicyTest na serwerze)', () => {
    it('accepts long passwords that are hard to guess', () => {
        for (const password of ['zielony-rower-nad-Wisłą', 'Mój kot śpi na parapecie', 'Gdzie-jest-Nemo?42']) {
            expect(isPasswordAcceptable(password, jan)).toBe(true);
        }
    });

    it('counts Polish letters as single characters but limits bytes', () => {
        expect(failed('Kot-na-dach')).toEqual(['length']);
        expect(failed('żółw-i-ślimak')).toEqual([]);
        expect(failed('ą'.repeat(37))).toContain('maxBytes');
        expect(failed('zielony-rower-'.repeat(5) + '!?')).toEqual([]);
    });

    it('rejects name or login regardless of case and Polish letters', () => {
        for (const password of ['jan-ma-rower-2026', 'KOWALSKI-rower-7', 'rower-JK123456-xy', 'kowalski z wisły']) {
            expect(failed(password)).toContain('personal');
        }
        expect(failed('lucja-ma-rower!', { email: 'lz1@tokstudiow.pl', firstName: 'Łucja', lastName: 'Żak' })).toEqual([
            'personal',
        ]);
    });

    it('rejects passwords from attack dictionaries', () => {
        for (const password of ['Tokstudiow-2026!', 'MojeHasło-zimowe', 'qwerty-rower-12', 'aaaaaaaabbbbbbbb', 'Zaq12wsx-rower!']) {
            expect(failed(password)).toEqual(['common']);
        }
    });

    it('marks every rule as not met for an empty field', () => {
        expect(failed('')).toEqual(['length', 'personal', 'common']);
    });
});
