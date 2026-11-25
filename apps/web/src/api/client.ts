import type { RowError, ScheduleConflict } from './types';

/**
 * Token dostępu żyje wyłącznie w pamięci tego modułu – nie w localStorage,
 * więc skrypt wstrzyknięty przez XSS nie znajdzie go w przeglądarce.
 * Refresh token jest w ciasteczku HttpOnly, niewidocznym dla JavaScriptu.
 */
let accessToken: string | null = null;
let onSessionExpired: (() => void) | null = null;

export function setSessionExpiredHandler(handler: (() => void) | null) {
    onSessionExpired = handler;
}
let refreshInFlight: Promise<boolean> | null = null;

export class ApiError extends Error {
    readonly status: number;
    /** błędy poszczególnych wierszy przy operacjach na wielu rekordach (np. imporcie) */
    readonly rows: RowError[];
    /** kolizje terminu w planie zajęć (409 „Schedule conflict”) */
    readonly conflicts: ScheduleConflict[];

    constructor(status: number, message: string, rows: RowError[] = [], conflicts: ScheduleConflict[] = []) {
        super(message);
        this.status = status;
        this.rows = rows;
        this.conflicts = conflicts;
    }
}

export function setAccessToken(token: string | null) {
    accessToken = token;
}

/**
 * Odświeża token dostępu na podstawie ciasteczka.
 * Jedno odświeżanie naraz w karcie (wspólna obietnica) i między kartami (Web Locks API):
 * przy rotacji tokenów dwa równoległe odświeżenia uznano by za kradzież tokenu.
 */
export function refreshAccessToken(): Promise<boolean> {
    if (!refreshInFlight) {
        refreshInFlight = navigator.locks
            .request('tokstudiow-token-refresh', async () => {
                const response = await fetch('/api/auth/refresh', {
                    method: 'POST',
                    headers: { 'X-Client': 'web' },
                });
                if (!response.ok) {
                    accessToken = null;
                    return false;
                }
                const body = await response.json();
                accessToken = body.accessToken;
                return true;
            })
            .catch(() => false)
            .finally(() => {
                refreshInFlight = null;
            });
    }
    return refreshInFlight;
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
    const headers = new Headers(init.headers);
    if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
    }
    if (init.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }

    const response = await fetch(path, { ...init, headers });

    // Token dostępu wygasł (15 min) – odśwież raz i powtórz żądanie
    if (response.status === 401) {
        // Token dostępu wygasł (15 min) – odśwież raz i powtórz żądanie
        if (retry && (await refreshAccessToken())) {
            return api<T>(path, init, false);
        }
        // Sesji nie da się odnowić – wyloguj w interfejsie
        onSessionExpired?.();
    }
    if (!response.ok) {
        const { detail, rows, conflicts } = await errorBody(response);
        throw new ApiError(response.status, detail, rows, conflicts);
    }
    if (response.status === 204) {
        return undefined as T;
    }
    return response.json() as Promise<T>;
}

async function errorBody(
    response: Response,
): Promise<{ detail: string; rows: RowError[]; conflicts: ScheduleConflict[] }> {
    try {
        const body = await response.json();
        return {
            detail: body.detail ?? response.statusText,
            rows: Array.isArray(body.rows) ? body.rows : [],
            conflicts: Array.isArray(body.conflicts) ? body.conflicts : [],
        };
    } catch {
        return { detail: response.statusText, rows: [], conflicts: [] };
    }
}