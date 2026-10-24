import { ApplicationResponse, ApplicationStatus } from './application.model';
import { DAY, durationBetween, shortDurationBetween } from './durations';

/**
 * Days in a status before an application counts as gone quiet; null shows the
 * age without ever flagging it. Closed statuses are absent: nothing is
 * expected to happen to them.
 */
export const STALE_AFTER_DAYS: Partial<Record<ApplicationStatus, number | null>> = {
  SAVED: 7, // still not applied
  APPLIED: 7, // worth a follow-up
  // Interview loops run weeks to months without a status change, so time in
  // status says nothing here. Follow-up dates are the better signal.
  INTERVIEWING: null,
  OFFER: 3, // offers come with deadlines
};

export interface Staleness {
  /** Compact time in the current status, e.g. "12d". */
  age: string;
  /** Sentence describing the age, for tooltips and screen readers. */
  description: string;
  stale: boolean;
}

/** Time-in-status details for an active application; null once it is closed. */
export function stalenessOf(app: ApplicationResponse, now: Date): Staleness | null {
  if (!(app.status in STALE_AFTER_DAYS)) return null;
  const limit = STALE_AFTER_DAYS[app.status] ?? null;

  const elapsed = now.getTime() - new Date(app.statusChangedAt).getTime();
  const stale = limit != null && elapsed >= limit * DAY;
  const inStatus = `In ${titleCase(app.status)} for ${durationBetween(app.statusChangedAt, now)}`;

  return {
    age: shortDurationBetween(app.statusChangedAt, now),
    description: stale ? `${inStatus}. No movement in over ${limit} days.` : `${inStatus}.`,
    stale,
  };
}

function titleCase(status: ApplicationStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}
