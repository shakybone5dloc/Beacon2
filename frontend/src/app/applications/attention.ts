import { ApplicationResponse, ApplicationStatus } from './application.model';
import { DAY, durationBetween, shortDurationBetween } from './durations';

/**
 * Days in a status before an application counts as gone quiet, used only when
 * no apply-by or follow-up date says otherwise. Null shows the age without
 * ever flagging it. Closed statuses are absent: nothing is expected of them.
 */
export const STALE_AFTER_DAYS: Partial<Record<ApplicationStatus, number | null>> = {
  SAVED: 7, // still not applied
  APPLIED: 7, // worth a follow-up
  // Interview loops run weeks to months without a status change, so time in
  // status says nothing here. Follow-up dates are the better signal.
  INTERVIEWING: null,
  OFFER: 3, // offers come with deadlines
};

/** How close an apply-by date gets before a Saved application is flagged. */
export const APPLY_BY_WARNING_DAYS = 3;

export interface DateSignal {
  /** Short badge text, e.g. "Closes in 2d" or "Follow-up 3d overdue". */
  label: string;
  urgent: boolean;
}

export interface Attention {
  /** Compact time in the current status, e.g. "12d". */
  age: string;
  /** Sentence describing the age, for tooltips and screen readers. */
  ageDescription: string;
  /** Apply-by countdown; only while the application is Saved. */
  deadline: DateSignal | null;
  followUp: DateSignal | null;
  needsAttention: boolean;
  /** Why it needs attention, when it does. */
  reason: string | null;
}

/**
 * What the board should say about an active application; null once closed.
 * Explicit dates win over the fixed STALE_AFTER_DAYS limits: a follow-up set
 * for next week means "leave it alone until then".
 */
export function attentionFor(app: ApplicationResponse, now: Date): Attention | null {
  if (!(app.status in STALE_AFTER_DAYS)) return null;

  const deadline = app.status === 'SAVED' && app.applyBy ? deadlineSignal(app.applyBy, now) : null;
  const followUp = app.followUpOn ? followUpSignal(app.followUpOn, now) : null;

  let reason: string | null = null;
  if (followUp?.urgent) {
    reason = followUp.label;
  } else if (deadline?.urgent) {
    reason = deadline.label;
  } else if (!deadline && !followUp) {
    const limit = STALE_AFTER_DAYS[app.status] ?? null;
    const elapsed = now.getTime() - new Date(app.statusChangedAt).getTime();
    if (limit != null && elapsed >= limit * DAY) {
      reason = `No movement in ${durationBetween(app.statusChangedAt, now)}`;
    }
  }

  return {
    age: shortDurationBetween(app.statusChangedAt, now),
    ageDescription: `In ${titleCase(app.status)} for ${durationBetween(app.statusChangedAt, now)}.`,
    deadline,
    followUp,
    needsAttention: reason != null,
    reason,
  };
}

function deadlineSignal(applyBy: string, now: Date): DateSignal {
  const days = daysUntil(applyBy, now);
  const label =
    days < 0 ? `Closed ${-days}d ago` : days === 0 ? 'Closes today' : `Closes in ${days}d`;
  return { label, urgent: days <= APPLY_BY_WARNING_DAYS };
}

function followUpSignal(followUpOn: string, now: Date): DateSignal {
  const days = daysUntil(followUpOn, now);
  const label =
    days < 0
      ? `Follow-up ${-days}d overdue`
      : days === 0
        ? 'Follow up today'
        : `Follow up in ${days}d`;
  return { label, urgent: days <= 0 };
}

/** Whole calendar days from the viewer's today to a YYYY-MM-DD date. */
export function daysUntil(date: string, now: Date): number {
  const [y, m, d] = date.split('-').map(Number);
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((Date.UTC(y, m - 1, d) - today) / DAY);
}

function titleCase(status: ApplicationStatus): string {
  return status.charAt(0) + status.slice(1).toLowerCase();
}
