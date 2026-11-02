import { AiUsageLineResponse, AiUsageMonthResponse, AiUsageResponse } from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type AiUsage = AiUsageResponse;
export type UsageLine = AiUsageLineResponse;
export type UsageMonth = AiUsageMonthResponse;

export const FEATURE_LABELS: Record<string, string> = {
  COVER_LETTER: 'Cover letters',
  FIT_SCORE: 'Fit checks',
};

/** Warn from here; the meter turns red once the budget is used up. */
export const BUDGET_WARNING = 0.8;

const dollars = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** "$1.23"; a few cents' worth of calls shows as "<$0.01" rather than "$0.00". */
export function usd(amount: number): string {
  return amount > 0 && amount < 0.005 ? '<$0.01' : dollars.format(amount);
}

/** "12.3k", "1.2M": token counts get large quickly. */
export function tokens(count: number): string {
  return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(
    count,
  );
}

/** "Nov" from "2026-11". */
export function monthLabel(month: string, style: 'short' | 'long' = 'short'): string {
  const [year, m] = month.split('-').map(Number);
  return new Date(year, m - 1, 1).toLocaleString('en-US', {
    month: style,
    ...(style === 'long' ? { year: 'numeric' } : {}),
  });
}

/** The browser's time zone, so "this month" starts when the user's month does. */
export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}
