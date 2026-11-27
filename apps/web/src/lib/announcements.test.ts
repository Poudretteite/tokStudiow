import { describe, expect, it } from 'vitest';
import type { AnnouncementTarget } from '../api/types';
import { sortTargets, targetLabel, wasEdited } from './announcements';

const group: AnnouncementTarget = { type: 'GROUP', id: 'g', name: 'Bazy danych', componentType: 'LAB', groupNumber: 2 };
const field: AnnouncementTarget = { type: 'FIELD_OF_STUDY', id: 'f', name: 'Informatyka', componentType: null, groupNumber: null };
const faculty: AnnouncementTarget = { type: 'FACULTY', id: 'w', name: 'Wydział Medyczny', componentType: null, groupNumber: null };

describe('announcements', () => {
    it('describes every kind of audience in Polish', () => {
        expect(targetLabel(group)).toBe('Bazy danych, laboratorium, gr. 2');
        expect(targetLabel(field)).toBe('Kierunek Informatyka');
        expect(targetLabel(faculty)).toBe('Wydział Medyczny');
        expect(targetLabel({ type: 'ALL', id: null, name: 'Cała uczelnia', componentType: null, groupNumber: null })).toBe(
            'Cała uczelnia',
        );
    });

    it('lists faculty first, then fields of study, then groups', () => {
        expect(sortTargets([group, field, faculty]).map((t) => t.type)).toEqual(['FACULTY', 'FIELD_OF_STUDY', 'GROUP']);
    });

    it('marks as edited only changes made later than a minute after publishing', () => {
        expect(wasEdited({ createdAt: '2026-11-25T10:00:00Z', updatedAt: '2026-11-25T10:00:00.4Z' })).toBe(false);
        expect(wasEdited({ createdAt: '2026-11-25T10:00:00Z', updatedAt: '2026-11-25T12:30:00Z' })).toBe(true);
    });
});
