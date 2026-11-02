import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type SavedSearch = api.SavedSearchResponse;
export type SearchRun = api.SearchRunResponse;
export type SaveSearchRequest = api.SaveSearchRequest;
export type JobMatch = api.JobMatchResponse;
export type DayOfWeek = SavedSearch['runDays'][number];
export type MatchStatus = 'NEW' | 'DISMISSED' | 'BELOW_MIN';

export const DAYS: { day: DayOfWeek; short: string }[] = [
  { day: 'MONDAY', short: 'Mon' },
  { day: 'TUESDAY', short: 'Tue' },
  { day: 'WEDNESDAY', short: 'Wed' },
  { day: 'THURSDAY', short: 'Thu' },
  { day: 'FRIDAY', short: 'Fri' },
  { day: 'SATURDAY', short: 'Sat' },
  { day: 'SUNDAY', short: 'Sun' },
];

export const WEEKDAYS: DayOfWeek[] = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];

/** "7 AM", "12 PM", "6 PM". */
export function hourLabel(hour: number): string {
  const suffix = hour < 12 ? 'AM' : 'PM';
  return `${hour % 12 === 0 ? 12 : hour % 12} ${suffix}`;
}

/** "Weekdays at 7 AM", "Every day at 6 PM", "Mon, Thu at 9 AM". */
export function scheduleLabel(days: readonly DayOfWeek[], hour: number): string {
  const set = new Set(days);
  const which =
    set.size === 7
      ? 'Every day'
      : set.size === 5 && WEEKDAYS.every((d) => set.has(d))
        ? 'Weekdays'
        : DAYS.filter((d) => set.has(d.day))
            .map((d) => d.short)
            .join(', ');
  return `${which} at ${hourLabel(hour)}`;
}

/** The browser's time zone; a search's run hour is in it. */
export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/** "Java remote" from "java remote", for a search named after its keywords. */
export function nameFromKeywords(keywords: string): string {
  const words = keywords.trim();
  return words ? words[0].toUpperCase() + words.slice(1) : '';
}
