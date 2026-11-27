import type { Announcement, AnnouncementTarget } from '../api/types';
import { COMPONENT_LABELS } from '../i18n/labels';

/** Klucz zapytania tablicy ogłoszeń – wspólny dla strony głównej i strony ogłoszeń. */
export const ANNOUNCEMENTS_KEY = ['announcements'];

/** Odbiorca ogłoszenia słowami: „Bazy danych, laboratorium, gr. 2”, „Kierunek Informatyka”… */
export function targetLabel(target: AnnouncementTarget): string {
    switch (target.type) {
        case 'GROUP':
            return target.componentType && target.groupNumber !== null
                ? `${target.name}, ${COMPONENT_LABELS[target.componentType].toLowerCase()}, gr. ${target.groupNumber}`
                : target.name;
        case 'FIELD_OF_STUDY':
            return `Kierunek ${target.name}`;
        case 'FACULTY':
            return target.name;
        case 'ALL':
            return 'Cała uczelnia';
    }
}

/** Poprawione później niż minutę po opublikowaniu – wtedy pokazujemy „edytowano”. */
export function wasEdited(announcement: Pick<Announcement, 'createdAt' | 'updatedAt'>): boolean {
    return new Date(announcement.updatedAt).getTime() - new Date(announcement.createdAt).getTime() > 60_000;
}

/** Kolejność w formularzu: wydział, kierunki, grupy (alfabetycznie), cała uczelnia. */
const ORDER: Record<AnnouncementTarget['type'], number> = { FACULTY: 0, FIELD_OF_STUDY: 1, GROUP: 2, ALL: 3 };

export function sortTargets(targets: AnnouncementTarget[]): AnnouncementTarget[] {
    return [...targets].sort(
        (a, b) => ORDER[a.type] - ORDER[b.type] || targetLabel(a).localeCompare(targetLabel(b), 'pl'),
    );
}
