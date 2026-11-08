import * as api from '../api/models';
import { ApplicationResponse, ApplicationStatus } from '../applications/application.model';
import { Attention, attentionFor, byUrgency } from '../applications/attention';
import { DAY } from '../applications/durations';
import { Deck } from '../study/study.model';
import { JobMatch } from '../searches/search.model';
import { WeekCount } from '../stats/stats.model';

export type HomeTodos = api.HomeTodosResponse;
export type HomeTodo = api.HomeTodoResponse;

/** How far ahead Coming up looks. */
export const UPCOMING_DAYS = 14;

/** How many new matches Home shows; the rest are on the Matches page. */
export const QUEUE_SIZE = 5;

/** The active stages, in board order, for This week's counts. */
export const ACTIVE_STAGES: { status: ApplicationStatus; label: string }[] = [
  { status: 'SAVED', label: 'Saved' },
  { status: 'APPLIED', label: 'Applied' },
  { status: 'INTERVIEWING', label: 'Interviewing' },
  { status: 'OFFER', label: 'Offer' },
];

/** "Good morning, Aaron": by the viewer's clock, with the first name when there is one. */
export function greeting(now: Date, displayName: string | null | undefined): string {
  const hour = now.getHours();
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const first = displayName?.trim().split(/\s+/)[0];
  return first ? `${part}, ${first}` : part;
}

/** Applications that need something from the user, most urgent first. */
export function needingAttention(
  apps: readonly ApplicationResponse[],
  now: Date,
): { app: ApplicationResponse; attention: Attention }[] {
  return apps
    .map((app) => ({ app, attention: attentionFor(app, now) }))
    .filter((card): card is { app: ApplicationResponse; attention: Attention } =>
      Boolean(card.attention?.needsAttention),
    )
    .sort(byUrgency);
}

export interface Upcoming {
  app: ApplicationResponse;
  startsAt: Date;
  /** Study cards due in the application's own deck; 0 when it has none. */
  cardsDue: number;
  deckId: string | null;
}

/** Interviews from now to UPCOMING_DAYS ahead, soonest first, with their study cards. */
export function upcomingInterviews(
  apps: readonly ApplicationResponse[],
  decks: readonly Deck[],
  now: Date,
): Upcoming[] {
  const until = now.getTime() + UPCOMING_DAYS * DAY;
  return apps
    .filter((app) => app.nextInterviewAt != null)
    .map((app) => {
      const deck = decks.find((d) => d.applicationId === app.id);
      return {
        app,
        startsAt: new Date(app.nextInterviewAt!),
        cardsDue: deck?.due ?? 0,
        deckId: deck?.id ?? null,
      };
    })
    .filter((u) => u.startsAt.getTime() >= now.getTime() && u.startsAt.getTime() <= until)
    .sort((a, b) => a.startsAt.getTime() - b.startsAt.getTime());
}

/** New matches, best fit first (unscored last), newest first among equals. */
export function queue(matches: readonly JobMatch[]): JobMatch[] {
  return [...matches]
    .sort(
      (a, b) =>
        (b.score ?? -1) - (a.score ?? -1) ||
        new Date(b.foundAt).getTime() - new Date(a.foundAt).getTime(),
    )
    .slice(0, QUEUE_SIZE);
}

/** Decks with cards due now, most due first. */
export function dueDecks(decks: readonly Deck[]): Deck[] {
  return decks.filter((d) => d.due > 0).sort((a, b) => b.due - a.due);
}

/** Applications sent this week and last, from Stats' weekly counts (the last entry is this week). */
export function sentThisWeek(weeks: readonly WeekCount[]): { thisWeek: number; lastWeek: number } {
  return {
    thisWeek: weeks.at(-1)?.applied ?? 0,
    lastWeek: weeks.at(-2)?.applied ?? 0,
  };
}

/** How many applications are in each active stage. */
export function stageCounts(
  apps: readonly ApplicationResponse[],
): { status: ApplicationStatus; label: string; count: number }[] {
  return ACTIVE_STAGES.map((stage) => ({
    ...stage,
    count: apps.filter((a) => a.status === stage.status).length,
  }));
}
