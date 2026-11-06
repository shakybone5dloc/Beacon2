import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Stats = api.StatsResponse;
export type StatsGroup = api.StatsGroupResponse;
export type StageTime = api.StageTimeResponse;
export type WeekCount = api.WeekCountResponse;

/** The ranges the page offers; null is all time. */
export const RANGES: { days: number | null; label: string }[] = [
  { days: null, label: 'All time' },
  { days: 90, label: 'Last 90 days' },
  { days: 30, label: 'Last 30 days' },
];

export const STAGE_LABELS: Record<string, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied, waiting',
  INTERVIEWING: 'Interviewing',
  OFFER: 'Offer',
};

/** "42%", or "–" when there's nothing to divide by. */
export function percent(part: number, whole: number): string {
  return whole > 0 ? `${Math.round((part / whole) * 100)}%` : '–';
}

/** "3.5 days", "1 day", "under a day", or "–" when unknown. */
export function days(value: number | null | undefined): string {
  if (value == null) return '–';
  if (value < 1) return 'under a day';
  return `${value} ${value === 1 ? 'day' : 'days'}`;
}

/** The funnel's steps, each as a share of the applications sent. */
export function funnel(group: StatsGroup): { label: string; count: number; share: number }[] {
  const base = group.applied;
  return [
    { label: 'Applied', count: group.applied },
    { label: 'Heard back', count: group.responded },
    { label: 'Interviewed', count: group.interviewed },
    { label: 'Offer', count: group.offered },
  ].map((step) => ({ ...step, share: base > 0 ? step.count / base : 0 }));
}
