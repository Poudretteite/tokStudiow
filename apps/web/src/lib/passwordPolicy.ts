/**
 * Zasady nowego hasła – lustro PasswordPolicy z serwera (NIST SP 800-63B: długość
 * i odporność na słownik zamiast wymuszania cyfr i znaków specjalnych).
 * Pozwala pokazać na bieżąco, czego brakuje; ostatecznie decyduje serwer.
 */

export const MIN_LENGTH = 12;
export const MAX_BYTES = 72;
const MIN_DISTINCT = 6;

const COMMON_FRAGMENTS = [
    'tokstudiow',
    'password',
    'haslo',
    'qwerty',
    'asdfgh',
    'zxcvbn',
    '123456',
    '654321',
    'abcdef',
    '1qaz2wsx',
    'zaq12wsx',
    'uniwersytet',
];

export interface PasswordOwner {
    email: string;
    firstName: string;
    lastName: string;
}

export interface PasswordRule {
    id: 'length' | 'maxBytes' | 'personal' | 'common';
    label: string;
    ok: boolean;
}

/** Małe litery bez polskich znaków i odstępów – tak samo jak na serwerze. */
function normalize(text: string): string {
    return text
        .toLowerCase()
        .normalize('NFD')
        .replace(/[̀-ͯ]/g, '')
        .replace(/ł/g, 'l')
        .replace(/\s/g, '');
}

function containsPersonalData(password: string, owner: PasswordOwner): boolean {
    const normalized = normalize(password);
    const login = owner.email.split('@')[0];
    return [login, owner.firstName, owner.lastName]
        .map(normalize)
        .some((part) => part.length >= 3 && normalized.includes(part));
}

function tooEasy(password: string): boolean {
    const normalized = normalize(password);
    return new Set(Array.from(password)).size < MIN_DISTINCT || COMMON_FRAGMENTS.some((f) => normalized.includes(f));
}

/** Lista zasad z informacją, które są już spełnione – do podpowiedzi pod polem hasła. */
export function passwordRules(password: string, owner: PasswordOwner): PasswordRule[] {
    return [
        { id: 'length', label: `co najmniej ${MIN_LENGTH} znaków`, ok: Array.from(password).length >= MIN_LENGTH },
        {
            id: 'maxBytes',
            label: 'nie dłuższe niż 72 bajty (ok. 70 znaków)',
            ok: new TextEncoder().encode(password).length <= MAX_BYTES,
        },
        { id: 'personal', label: 'bez imienia, nazwiska i loginu', ok: password !== '' && !containsPersonalData(password, owner) },
        {
            id: 'common',
            label: 'nie do odgadnięcia (bez „qwerty”, „123456”, „hasło”, nazwy systemu i powtórzeń)',
            ok: password !== '' && !tooEasy(password),
        },
    ];
}

export function isPasswordAcceptable(password: string, owner: PasswordOwner): boolean {
    return passwordRules(password, owner).every((rule) => rule.ok);
}
