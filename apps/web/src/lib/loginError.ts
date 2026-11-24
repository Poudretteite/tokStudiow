/**
 * Komunikat po nieudanym logowaniu. Celowo jeden dla złego hasła i nieistniejącego konta,
 * żeby nie zdradzać, które adresy są w systemie. Przy blokadzie (429) – za ile minut spróbować.
 */
export function loginErrorMessage(status: number, retryAfter: string | null): string {
    if (status === 429) {
        const seconds = Number(retryAfter);
        const minutes = Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds / 60) : null;
        const when = minutes === null ? 'za kilka minut' : minutes === 1 ? 'za minutę' : `za ${minutes} min`;
        return `Zbyt wiele nieudanych prób logowania. Ze względów bezpieczeństwa spróbuj ponownie ${when}.`;
    }
    if (status === 400 || status === 401) {
        return 'Nieprawidłowy adres e-mail lub hasło.';
    }
    return 'Logowanie jest chwilowo niedostępne. Spróbuj ponownie za chwilę.';
}
