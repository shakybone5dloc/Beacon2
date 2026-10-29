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
  // status says nothing here. Interview dates and follow-ups are the signal
  // (see QUIET_AFTER_INTERVIEW_DAYS).
  INTERVIEWING: null,
  OFFER: 3, // offers come with deadlines
};

/** How close an apply-by date gets before a Saved application is flagged. */
export const APPLY_BY_WARNING_DAYS = 3;

/** Days after the last interview, with none scheduled, before an Interviewing application is flagged. */
export const QUIET_AFTER_INTERVIEW_DAYS = 7;

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
  /** The next interview, e.g. "Interview tomorrow 2:00 PM"; never urgent. */
  interview: DateSignal | null;
  needsAttention: boolean;
  /** Why it needs attention, when it does. */
  reason: string | null;
}

/**
 * What the board should say about an active application; null once closed.
 * Explicit dates win over the fixed STALE_AFTER_DAYS limits: a follow-up set
 * for next week means "leave it alone until then", and so does a scheduled
 * interview. An Interviewing application with no interview ahead is flagged
 * once its last interview is QUIET_AFTER_INTERVIEW_DAYS old.
 */
export function attentionFor(app: ApplicationResponse, now: Date): Attention | null {
  if (!(app.status in STALE_AFTER_DAYS)) return null;

  const deadline = app.status === 'SAVED' && app.applyBy ? deadlineSignal(app.applyBy, now) : null;
  const followUp = app.followUpOn ? followUpSignal(app.followUpOn, now) : null;
  const interview = app.nextInterviewAt ? interviewSignal(app.nextInterviewAt, now) : null;

  let reason: string | null = null;
  if (followUp?.urgent) {
    reason = followUp.label;
  } else if (deadline?.urgent) {
    reason = deadline.label;
  } else if (!deadline && !followUp && !interview) {
    const lastInterviewAt = app.status === 'INTERVIEWING' ? app.lastInterviewAt : null;
    const limit = lastInterviewAt
      ? QUIET_AFTER_INTERVIEW_DAYS
      : (STALE_AFTER_DAYS[app.status] ?? null);
    const since = lastInterviewAt ?? app.statusChangedAt;
    const elapsed = now.getTime() - new Date(since).getTime();
    if (limit != null && elapsed >= limit * DAY) {
      reason = lastInterviewAt
        ? `No news ${durationBetween(since, now)} after the interview`
        : `No movement in ${durationBetween(since, now)}`;
    }
  }

  return {
    age: shortDurationBetween(app.statusChangedAt, now),
    ageDescription: `In ${titleCase(app.status)} for ${durationBetween(app.statusChangedAt, now)}.`,
    deadline,
    followUp,
    interview,
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

/** "Interview today 2:00 PM", "tomorrow", a weekday within the week, then a date. */
function interviewSignal(startsAt: string, now: Date): DateSignal {
  const start = new Date(startsAt);
  const time = start.toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
  const days = daysUntil(localDate(start), now);
  const when =
    days === 0
      ? `today ${time}`
      : days === 1
        ? `tomorrow ${time}`
        : days < 7
          ? `${start.toLocaleDateString(undefined, { weekday: 'short' })} ${time}`
          : start.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
  return { label: `Interview ${when}`, urgent: false };
}

/** YYYY-MM-DD of an instant in the viewer's time zone. */
function localDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
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
