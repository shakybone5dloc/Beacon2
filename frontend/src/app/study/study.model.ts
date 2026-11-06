import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Deck = api.DeckResponse;
export type Card = api.CardResponse;
export type MadeCards = api.MadeCardsResponse;
export type Grade = api.ReviewCardRequest['grade'];

/** The four grades, with the key that gives each in a study session. */
export const GRADES: { grade: Grade; label: string; key: string; hint: string }[] = [
  { grade: 'AGAIN', label: 'Again', key: '1', hint: 'Forgot it; back in a few minutes' },
  { grade: 'HARD', label: 'Hard', key: '2', hint: 'Got there, with effort' },
  { grade: 'GOOD', label: 'Good', key: '3', hint: 'Knew it' },
  { grade: 'EASY', label: 'Easy', key: '4', hint: 'Knew it straight away' },
];

/** "in 3 days", "tomorrow", "now": when a card is next due, from the viewer's now. */
export function dueLabel(dueAt: string, now: Date): string {
  const ms = new Date(dueAt).getTime() - now.getTime();
  // Within a minute counts as now: a card kept a moment ago shouldn't say "in 0 min".
  if (ms < 60_000) return 'now';
  const minutes = Math.round(ms / 60_000);
  if (minutes < 60) return `in ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `in ${hours} h`;
  const days = Math.round(hours / 24);
  return days === 1 ? 'tomorrow' : `in ${days} days`;
}
