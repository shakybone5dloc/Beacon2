import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Interview = api.InterviewResponse;
export type ScheduleInterviewRequest = api.ScheduleInterviewRequest;
export type UpdateInterviewRequest = api.UpdateInterviewRequest;

export type InterviewType = Interview['type'];
export type InterviewOutcome = NonNullable<Interview['outcome']>;

export const INTERVIEW_TYPE_LABELS: Record<InterviewType, string> = {
  PHONE_SCREEN: 'Phone screen',
  TECHNICAL: 'Technical',
  PANEL: 'Panel',
  ONSITE: 'Onsite',
  OTHER: 'Other',
};
export const INTERVIEW_TYPES = Object.keys(INTERVIEW_TYPE_LABELS) as InterviewType[];

export const OUTCOME_LABELS: Record<InterviewOutcome, string> = {
  PASSED: 'Passed',
  DID_NOT_PASS: 'Did not pass',
  CANCELLED: 'Cancelled',
};
export const OUTCOMES = Object.keys(OUTCOME_LABELS) as InterviewOutcome[];

/** Minutes offered for the length of an interview. */
export const DURATIONS = [15, 30, 45, 60, 90, 120, 180, 240, 360, 480];

export function durationLabel(minutes: number): string {
  if (minutes < 60) return `${minutes} min`;
  const hours = minutes / 60;
  return Number.isInteger(hours) ? `${hours} h` : `${Math.floor(hours)} h ${minutes % 60} min`;
}

/** The viewer's own IANA zone, e.g. "America/Chicago". */
export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
}

/** Every zone the browser knows, with the viewer's and the given ones sure to be in it. */
export function timeZones(...include: string[]): string[] {
  const known =
    typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : ['UTC'];
  return [...new Set([...known, browserTimeZone(), ...include])].sort();
}

/** "Thu, Mar 10, 2:00 PM CDT": the start as agreed, in the interview's own zone. */
export function formatInZone(instant: string, timeZone: string): string {
  return new Date(instant).toLocaleString(undefined, {
    timeZone,
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  });
}

/** "1:00 PM" in the viewer's zone, or null when that's the interview's zone anyway. */
export function viewerTime(instant: string, timeZone: string): string | null {
  const viewer = browserTimeZone();
  if (sameOffset(instant, timeZone, viewer)) return null;
  return new Date(instant).toLocaleString(undefined, {
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  });
}

function sameOffset(instant: string, a: string, b: string): boolean {
  const options: Intl.DateTimeFormatOptions = { timeZoneName: 'longOffset' };
  const date = new Date(instant);
  return (
    date.toLocaleString('en-US', { ...options, timeZone: a }) ===
    date.toLocaleString('en-US', { ...options, timeZone: b })
  );
}

/** "2026-10-30T14:00" from the API's "2026-10-30T14:00:00", for a datetime-local input. */
export function toInputValue(localStart: string): string {
  return localStart.slice(0, 16);
}
