const dateFormat = new Intl.DateTimeFormat('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' });
const averageFormat = new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Data z API (RRRR-MM-DD) – bez strefy czasowej, żeby nie przesunęła się o dzień. */
export function formatDate(isoDate: string): string {
    const [year, month, day] = isoDate.split('-').map(Number);
    return dateFormat.format(new Date(year, month - 1, day));
}

export function formatTime(time: string): string {
    return time.slice(0, 5);
}

export function formatAverage(value: number): string {
    return averageFormat.format(value);
}