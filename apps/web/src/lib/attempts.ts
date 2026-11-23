/** Próby z jednego elementu zaliczenia, od pierwszej: ostatnia się liczy, wcześniejsze to historia poprawy. */
export function splitAttempts<T extends { attempt: number }>(grades: T[]): { current: T | undefined; earlier: T[] } {
    const sorted = [...grades].sort((a, b) => a.attempt - b.attempt);
    return { current: sorted.at(-1), earlier: sorted.slice(0, -1) };
}
