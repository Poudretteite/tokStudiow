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

    constructor(status: number, message: string) {
        super(message);
        this.status = status;
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
        throw new ApiError(response.status, await errorDetail(response));
    }
    if (response.status === 204) {
        return undefined as T;
    }
    return response.json() as Promise<T>;
}

async function errorDetail(response: Response): Promise<string> {
    try {
        const body = await response.json();
        return body.detail ?? response.statusText;
    } catch {
        return response.statusText;
    }
}