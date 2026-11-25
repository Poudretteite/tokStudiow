import { describe, expect, it } from 'vitest';
import type { ScheduleEntry } from '../api/types';
import {
    appliesInWeek,
    conflictText,
    formatWeeks,
    frequencyText,
    hourRange,
    placeDay,
    termWeek,
    timeRange,
    visibleDays,
    weekLetter,
    weekMonday,
} from './schedule';

function entry(overrides: Partial<ScheduleEntry>): ScheduleEntry {
    return {
        id: 'e',
        groupId: 'g',
        courseCode: 'INF-1',
        courseName: 'Bazy danych',
        componentType: 'LAB',
        groupNumber: 2,
        lecturerName: 'dr Jan Kowalski',
        dayOfWeek: 1,
        startTime: '08:00:00',
        endTime: '09:30:00',
        frequency: 'WEEKLY',
        weeks: [],
        roomId: 'r',
        roomLabel: 'A, s. 101',
        ...overrides,
    };
}

describe('termWeek', () => {
    // semestr zimowy 2026/27 zaczyna się w czwartek 1.10.2026
    it('counts weeks from the Monday of the week the term starts', () => {
        expect(termWeek('2026-10-01', '2026-10-01')).toBe(1);
        expect(termWeek('2026-10-01', '2026-10-04')).toBe(1); // niedziela tego samego tygodnia
        expect(termWeek('2026-10-01', '2026-10-05')).toBe(2);
        expect(termWeek('2026-10-01', '2026-11-24')).toBe(9);
        expect(termWeek('2026-10-01', '2026-09-28')).toBe(1);
        expect(termWeek('2026-10-01', '2026-09-27')).toBe(0);
    });

    it('works across the change to winter time and the new year', () => {
        expect(termWeek('2026-10-01', '2026-10-26')).toBe(5);
        expect(termWeek('2026-10-01', '2027-01-04')).toBe(15);
    });

    it('gives the Monday of a term week', () => {
        expect(weekMonday('2026-10-01', 1)).toBe('2026-09-28');
        expect(weekMonday('2026-10-01', 9)).toBe('2026-11-23');
    });
});

describe('weeks A/B and selected weeks', () => {
    it('shows week A, week B and selected week classes only in their weeks', () => {
        expect(appliesInWeek({ frequency: 'WEEKLY', weeks: [] }, 4)).toBe(true);
        expect(appliesInWeek({ frequency: 'ODD_WEEKS', weeks: [] }, 1)).toBe(true);
        expect(appliesInWeek({ frequency: 'ODD_WEEKS', weeks: [] }, 2)).toBe(false);
        expect(appliesInWeek({ frequency: 'EVEN_WEEKS', weeks: [] }, 2)).toBe(true);
        expect(appliesInWeek({ frequency: 'EVEN_WEEKS', weeks: [] }, 3)).toBe(false);
        expect(appliesInWeek({ frequency: 'SELECTED_WEEKS', weeks: [1, 4] }, 4)).toBe(true);
        expect(appliesInWeek({ frequency: 'SELECTED_WEEKS', weeks: [1, 4] }, 3)).toBe(false);
    });

    it('names weeks A (odd) and B (even)', () => {
        expect(weekLetter(1)).toBe('A');
        expect(weekLetter(2)).toBe('B');
        expect(weekLetter(9)).toBe('A');
    });

    it('describes selected weeks compactly', () => {
        expect(formatWeeks([9, 1, 2, 3, 5, 10])).toBe('1–3, 5, 9–10');
        expect(frequencyText({ frequency: 'SELECTED_WEEKS', weeks: [7] })).toBe('tydzień 7');
        expect(frequencyText({ frequency: 'SELECTED_WEEKS', weeks: [1, 2, 4] })).toBe('tygodnie 1–2, 4');
        expect(frequencyText({ frequency: 'EVEN_WEEKS', weeks: [] })).toBe('tydzień B');
    });
});

describe('grid layout', () => {
    it('shows weekend only when there are classes', () => {
        expect(visibleDays([entry({})])).toEqual([1, 2, 3, 4, 5]);
        expect(visibleDays([entry({ dayOfWeek: 6 })])).toEqual([1, 2, 3, 4, 5, 6]);
    });

    it('widens the hour range to full hours around classes', () => {
        expect(hourRange([])).toEqual({ from: 480, to: 960 });
        expect(hourRange([entry({ startTime: '07:30', endTime: '18:15' })])).toEqual({ from: 420, to: 1140 });
    });

    it('puts overlapping classes side by side and back-to-back ones in one column', () => {
        const odd = entry({ id: 'odd', frequency: 'ODD_WEEKS' });
        const even = entry({ id: 'even', frequency: 'EVEN_WEEKS' });
        const next = entry({ id: 'next', startTime: '09:30', endTime: '11:00' });
        const placed = placeDay([next, even, odd]);
        const byId = Object.fromEntries(placed.map((p) => [p.entry.id, p]));
        expect(byId.odd.lanes).toBe(2);
        expect(byId.even.lanes).toBe(2);
        expect(new Set([byId.odd.lane, byId.even.lane])).toEqual(new Set([0, 1]));
        expect(byId.next).toMatchObject({ lane: 0, lanes: 1 });
    });
});

describe('texts', () => {
    it('formats times without leading zero', () => {
        expect(timeRange(entry({}))).toBe('8:00–9:30');
    });

    it('describes conflicts in Polish', () => {
        expect(conflictText({ type: 'ROOM', entry: entry({ frequency: 'ODD_WEEKS' }), studentCount: null })).toBe(
            'Sala A, s. 101 jest zajęta: Bazy danych (laboratorium, gr. 2), poniedziałek 8:00–9:30, tydzień A.',
        );
        expect(conflictText({ type: 'STUDENTS', entry: entry({}), studentCount: 3 })).toContain('Studenci tej grupy (3)');
    });
});
