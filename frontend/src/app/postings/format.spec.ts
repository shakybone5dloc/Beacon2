import { formatLocation, formatPosted, formatSalary } from './format';
import { aPosting } from './testing';

describe('formatSalary', () => {
  it.each([
    [
      { salaryMin: 140000, salaryMax: 170000, salaryCurrency: 'USD', salaryPeriod: 'year' },
      '$140k–$170k/yr',
    ],
    [{ salaryMin: 45, salaryMax: 45, salaryCurrency: 'USD', salaryPeriod: 'hour' }, '$45/hr'],
    [{ salaryMin: 60000, salaryMax: null, salaryCurrency: 'GBP', salaryPeriod: 'year' }, '£60k/yr'],
    [
      { salaryMin: 90000, salaryMax: 110000, salaryCurrency: 'INR', salaryPeriod: 'month' },
      'INR 90k–INR 110k/mo',
    ],
    [{ salaryText: '$1000 per task' }, '$1000 per task'],
    [{}, null],
  ])('%o reads %s', (overrides, expected) => {
    expect(formatSalary(aPosting(overrides))).toBe(expected);
  });
});

describe('formatLocation', () => {
  it('keeps short locations as they are', () => {
    expect(formatLocation('United States, Canada')).toBe('United States, Canada');
  });

  it('shortens long country lists', () => {
    expect(formatLocation('Albania, Andorra, Argentina, Australia, Austria')).toBe(
      'Albania, Andorra, Argentina +2 more',
    );
  });

  it('is null without a location', () => {
    expect(formatLocation(null)).toBeNull();
  });
});

describe('formatPosted', () => {
  it('reads as a short age', () => {
    const now = new Date('2026-10-24T12:00:00Z');

    expect(formatPosted(aPosting({ postedAt: '2026-10-21T12:00:00Z' }), now)).toBe('3d ago');
    expect(formatPosted(aPosting({ postedAt: null }), now)).toBeNull();
  });
});
