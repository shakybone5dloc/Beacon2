import { anApplication } from './testing';
import { stalenessOf } from './staleness';

describe('stalenessOf', () => {
  const now = new Date('2026-10-20T12:00:00Z');

  it('is fresh just under the limit', () => {
    const app = anApplication({ status: 'APPLIED', statusChangedAt: '2026-10-13T13:00:00Z' });

    expect(stalenessOf(app, now)).toEqual({
      age: '6d',
      description: 'In Applied for 6 days.',
      stale: false,
    });
  });

  it('is stale once the limit is reached', () => {
    const app = anApplication({ status: 'APPLIED', statusChangedAt: '2026-10-13T12:00:00Z' });

    expect(stalenessOf(app, now)).toEqual({
      age: '7d',
      description: 'In Applied for 7 days. No movement in over 7 days.',
      stale: true,
    });
  });

  it('uses a per-status limit', () => {
    const fourDays = '2026-10-16T12:00:00Z';

    expect(
      stalenessOf(anApplication({ status: 'OFFER', statusChangedAt: fourDays }), now)?.stale,
    ).toBe(true);
    expect(
      stalenessOf(anApplication({ status: 'SAVED', statusChangedAt: fourDays }), now)?.stale,
    ).toBe(false);
  });

  it('shows the age of an interviewing application but never flags it', () => {
    const app = anApplication({ status: 'INTERVIEWING', statusChangedAt: '2026-08-21T12:00:00Z' });

    expect(stalenessOf(app, now)).toEqual({
      age: '60d',
      description: 'In Interviewing for 60 days.',
      stale: false,
    });
  });

  it('is null for closed applications', () => {
    const app = anApplication({ status: 'REJECTED', statusChangedAt: '2026-01-01T00:00:00Z' });

    expect(stalenessOf(app, now)).toBeNull();
  });
});
