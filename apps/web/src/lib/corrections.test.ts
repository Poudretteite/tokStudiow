import { describe, expect, it } from 'vitest';
import {
    finalGradeDeadline,
    lecturerMayCorrect,
    partialGradeDeadline,
    sessionSummary,
    todayAtUniversity,
} from './corrections';

describe('lecturerMayCorrect', () => {
    it('allows corrections up to and including the deadline day', () => {
        expect(lecturerMayCorrect('2027-02-17', '2027-02-16')).toBe(true);
        expect(lecturerMayCorrect('2027-02-17', '2027-02-17')).toBe(true);
        expect(lecturerMayCorrect('2027-02-17', '2027-02-18')).toBe(false);
    });
});

describe('todayAtUniversity', () => {
    it('counts the day in Warsaw time, not in UTC', () => {
        // 23:30 UTC 22 listopada to już 00:30 23 listopada w Polsce
        expect(todayAtUniversity(new Date('2026-11-22T23:30:00Z'))).toBe('2026-11-23');
        expect(todayAtUniversity(new Date('2026-11-22T12:00:00Z'))).toBe('2026-11-22');
    });
});

describe('deadlines per grade', () => {
    const dates = {
        examSessionEnd: '2027-02-15',
        correctionDeadline: '2027-02-17',
        retakeSessionEnd: '2027-03-01',
        retakeCorrectionDeadline: '2027-03-03',
    };

    it('gives partial grades entered by the session end the session deadline', () => {
        // 15 lutego 23:30 w Polsce to jeszcze sesja, choć w UTC to 22:30
        expect(partialGradeDeadline(dates, '2027-02-15T22:30:00Z')).toBe('2027-02-17');
        expect(partialGradeDeadline(dates, '2027-01-20T10:00:00Z')).toBe('2027-02-17');
    });

    it('gives partial grades entered after the session the retake deadline', () => {
        // 15 lutego 23:30 UTC to już 16 lutego w Polsce
        expect(partialGradeDeadline(dates, '2027-02-15T23:30:00Z')).toBe('2027-03-03');
    });

    it('gives retake and commission final grades the retake deadline', () => {
        expect(finalGradeDeadline(dates, 'FIRST')).toBe('2027-02-17');
        expect(finalGradeDeadline(dates, 'SECOND')).toBe('2027-03-03');
        expect(finalGradeDeadline(dates, 'COMMISSION')).toBe('2027-03-03');
    });

    it('mentions the retake session only when there is a separate one', () => {
        expect(sessionSummary(dates, true)).toContain('sesja poprawkowa');
        expect(sessionSummary({ ...dates, retakeSessionEnd: '2027-02-15' }, false)).not.toContain('poprawkowa');
    });
});
