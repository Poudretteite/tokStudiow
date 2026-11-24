import { describe, expect, it } from 'vitest';
import { loginErrorMessage } from './loginError';

describe('loginErrorMessage', () => {
    it('gives one message for wrong password and unknown account', () => {
        expect(loginErrorMessage(401, null)).toBe('Nieprawidłowy adres e-mail lub hasło.');
        expect(loginErrorMessage(400, null)).toBe(loginErrorMessage(401, null));
    });

    it('tells when to try again after too many attempts, rounding up to minutes', () => {
        expect(loginErrorMessage(429, '900')).toContain('za 15 min');
        expect(loginErrorMessage(429, '61')).toContain('za 2 min');
        expect(loginErrorMessage(429, '30')).toContain('za minutę');
        expect(loginErrorMessage(429, null)).toContain('za kilka minut');
        expect(loginErrorMessage(429, 'abc')).toContain('za kilka minut');
    });

    it('does not blame the password when the server fails', () => {
        expect(loginErrorMessage(503, null)).toBe('Logowanie jest chwilowo niedostępne. Spróbuj ponownie za chwilę.');
    });
});
