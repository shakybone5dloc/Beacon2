import { durationBetween, shortDurationBetween } from './durations';

describe('durationBetween', () => {
  const start = '2026-10-01T12:00:00Z';

  it.each([
    ['2026-10-01T12:30:00Z', 'less than an hour'],
    ['2026-10-01T13:00:00Z', '1 hour'],
    ['2026-10-01T17:59:00Z', '5 hours'],
    ['2026-10-02T12:00:00Z', '1 day'],
    ['2026-10-04T11:00:00Z', '2 days'],
    ['2026-10-31T12:00:00Z', '30 days'],
  ])('to %s is %s', (end, expected) => {
    expect(durationBetween(start, end)).toBe(expected);
  });

  it('treats a negative gap as less than an hour', () => {
    expect(durationBetween('2026-10-02T00:00:00Z', start)).toBe('less than an hour');
  });
});

describe('shortDurationBetween', () => {
  const start = '2026-10-01T12:00:00Z';

  it.each([
    ['2026-10-01T12:59:00Z', '<1h'],
    ['2026-10-01T17:00:00Z', '5h'],
    ['2026-10-02T12:00:00Z', '1d'],
    ['2026-10-13T12:00:00Z', '12d'],
  ])('to %s is %s', (end, expected) => {
    expect(shortDurationBetween(start, end)).toBe(expected);
  });
});
