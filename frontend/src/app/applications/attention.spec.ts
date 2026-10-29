import { anApplication } from './testing';
import { attentionFor, daysUntil } from './attention';

describe('attentionFor', () => {
  // Local noon, so "today" is Oct 20 in any timezone the tests run in.
  const now = new Date(2026, 9, 20, 12, 0, 0);
  const daysAgo = (days: number) => new Date(now.getTime() - days * 86_400_000).toISOString();

  it('is null for closed applications', () => {
    const app = anApplication({ status: 'REJECTED', followUpOn: '2026-10-01' });

    expect(attentionFor(app, now)).toBeNull();
  });

  describe('without dates', () => {
    it('is fine just under the status limit', () => {
      const app = anApplication({ status: 'APPLIED', statusChangedAt: daysAgo(6.9) });

      expect(attentionFor(app, now)).toMatchObject({
        age: '6d',
        ageDescription: 'In Applied for 6 days.',
        needsAttention: false,
        reason: null,
      });
    });

    it('needs attention once the status limit is reached', () => {
      const app = anApplication({ status: 'APPLIED', statusChangedAt: daysAgo(7) });

      expect(attentionFor(app, now)).toMatchObject({
        needsAttention: true,
        reason: 'No movement in 7 days',
      });
    });

    it('uses a per-status limit', () => {
      const fourDays = daysAgo(4);

      expect(
        attentionFor(anApplication({ status: 'OFFER', statusChangedAt: fourDays }), now),
      ).toMatchObject({ needsAttention: true });
      expect(
        attentionFor(anApplication({ status: 'SAVED', statusChangedAt: fourDays }), now),
      ).toMatchObject({ needsAttention: false });
    });

    it('shows the age of an interviewing application but never flags it', () => {
      const app = anApplication({ status: 'INTERVIEWING', statusChangedAt: daysAgo(60) });

      expect(attentionFor(app, now)).toMatchObject({ age: '60d', needsAttention: false });
    });
  });

  describe('interviews', () => {
    const at = (day: number, hour: number) => new Date(2026, 9, day, hour, 0, 0).toISOString();

    it.each([
      [at(20, 15), 'Interview today 3:00 PM'],
      [at(21, 14), 'Interview tomorrow 2:00 PM'],
      [at(23, 9), 'Interview Fri 9:00 AM'],
      [at(30, 9), 'Interview Oct 30'],
    ])('a next interview at %s reads "%s"', (nextInterviewAt, label) => {
      const app = anApplication({ status: 'INTERVIEWING', nextInterviewAt });

      expect(attentionFor(app, now)?.interview).toEqual({ label, urgent: false });
    });

    it('holds off the age rule while one is scheduled, whatever the status', () => {
      const app = anApplication({
        status: 'APPLIED',
        statusChangedAt: daysAgo(30),
        nextInterviewAt: at(24, 10),
      });

      expect(attentionFor(app, now)?.needsAttention).toBe(false);
    });

    it('flags an interviewing application a week after its last interview', () => {
      const quiet = anApplication({
        status: 'INTERVIEWING',
        statusChangedAt: daysAgo(40),
        lastInterviewAt: daysAgo(9),
      });
      const recent = anApplication({
        status: 'INTERVIEWING',
        statusChangedAt: daysAgo(40),
        lastInterviewAt: daysAgo(6),
      });

      expect(attentionFor(quiet, now)).toMatchObject({
        needsAttention: true,
        reason: 'No news 9 days after the interview',
      });
      expect(attentionFor(recent, now)?.needsAttention).toBe(false);
    });

    it('lets a follow-up date win over the last interview', () => {
      const app = anApplication({
        status: 'INTERVIEWING',
        lastInterviewAt: daysAgo(20),
        followUpOn: '2026-10-25',
      });

      expect(attentionFor(app, now)?.needsAttention).toBe(false);
    });
  });

  describe('apply-by date', () => {
    it.each([
      ['2026-10-30', 'Closes in 10d', false],
      ['2026-10-23', 'Closes in 3d', true],
      ['2026-10-20', 'Closes today', true],
      ['2026-10-18', 'Closed 2d ago', true],
    ])('%s reads "%s"', (applyBy, label, urgent) => {
      const app = anApplication({ status: 'SAVED', applyBy });

      expect(attentionFor(app, now)?.deadline).toEqual({ label, urgent });
      expect(attentionFor(app, now)?.needsAttention).toBe(urgent);
    });

    it('replaces the age rule while the deadline is far off', () => {
      const app = anApplication({
        status: 'SAVED',
        statusChangedAt: daysAgo(20),
        applyBy: '2026-11-30',
      });

      expect(attentionFor(app, now)?.needsAttention).toBe(false);
    });

    it('is ignored once the application has moved past Saved', () => {
      const app = anApplication({ status: 'APPLIED', applyBy: '2026-10-18' });

      expect(attentionFor(app, now)?.deadline).toBeNull();
    });
  });

  describe('follow-up date', () => {
    it.each([
      ['2026-10-25', 'Follow up in 5d', false],
      ['2026-10-20', 'Follow up today', true],
      ['2026-10-17', 'Follow-up 3d overdue', true],
    ])('%s reads "%s"', (followUpOn, label, urgent) => {
      const app = anApplication({ status: 'APPLIED', followUpOn });

      expect(attentionFor(app, now)?.followUp).toEqual({ label, urgent });
    });

    it('flags an interviewing application once due', () => {
      const app = anApplication({ status: 'INTERVIEWING', followUpOn: '2026-10-19' });

      expect(attentionFor(app, now)).toMatchObject({
        needsAttention: true,
        reason: 'Follow-up 1d overdue',
      });
    });

    it('holds off the age rule until the date', () => {
      const app = anApplication({
        status: 'APPLIED',
        statusChangedAt: daysAgo(30),
        followUpOn: '2026-10-27',
      });

      expect(attentionFor(app, now)?.needsAttention).toBe(false);
    });

    it('wins over the deadline as the reason when both are urgent', () => {
      const app = anApplication({
        status: 'SAVED',
        applyBy: '2026-10-21',
        followUpOn: '2026-10-20',
      });

      expect(attentionFor(app, now)?.reason).toBe('Follow up today');
    });
  });
});

describe('daysUntil', () => {
  it('counts calendar days from local today, ignoring the time of day', () => {
    expect(daysUntil('2026-10-21', new Date(2026, 9, 20, 23, 59))).toBe(1);
    expect(daysUntil('2026-10-21', new Date(2026, 9, 21, 0, 1))).toBe(0);
  });
});
