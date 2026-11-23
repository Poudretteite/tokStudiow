import { describe, expect, it } from 'vitest';
import { splitAttempts } from './attempts';

describe('splitAttempts', () => {
    it('treats the latest attempt as the grade and the rest as retake history', () => {
        const { current, earlier } = splitAttempts([
            { attempt: 2, label: '3,0' },
            { attempt: 1, label: '2,0' },
            { attempt: 3, label: '4,0' },
        ]);
        expect(current?.label).toBe('4,0');
        expect(earlier.map((g) => g.label)).toEqual(['2,0', '3,0']);
    });

    it('has no grade when there are no attempts', () => {
        expect(splitAttempts([])).toEqual({ current: undefined, earlier: [] });
    });
});
