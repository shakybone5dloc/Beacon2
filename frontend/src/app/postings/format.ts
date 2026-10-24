import { JobPosting } from './posting.model';
import { shortDurationBetween } from '../applications/durations';

const SYMBOLS: Record<string, string> = { USD: '$', CAD: 'CA$', AUD: 'A$', GBP: '£', EUR: '€' };
const PERIODS: Record<string, string> = {
  year: '/yr',
  month: '/mo',
  week: '/wk',
  day: '/day',
  hour: '/hr',
};

/** The salary fields postings and applications share. */
export type SalaryFields = Pick<
  JobPosting,
  'salaryMin' | 'salaryMax' | 'salaryCurrency' | 'salaryPeriod' | 'salaryText'
>;

/** e.g. "$140k–$170k/yr", the source's own text, or null when there is nothing to show. */
export function formatSalary(p: SalaryFields): string | null {
  if (p.salaryMin == null && p.salaryMax == null) return p.salaryText;

  const symbol = p.salaryCurrency ? (SYMBOLS[p.salaryCurrency] ?? `${p.salaryCurrency} `) : '';
  const amount = (n: number) => (n >= 1000 ? `${symbol}${Math.round(n / 1000)}k` : `${symbol}${n}`);
  const range =
    p.salaryMin != null && p.salaryMax != null && p.salaryMin !== p.salaryMax
      ? `${amount(p.salaryMin)}–${amount(p.salaryMax)}`
      : amount((p.salaryMin ?? p.salaryMax)!);

  return range + (p.salaryPeriod ? (PERIODS[p.salaryPeriod] ?? '') : '');
}

/** Long country lists (some remote jobs allow 40+) shortened to the first few. */
export function formatLocation(location: string | null, shown = 3): string | null {
  if (!location) return null;
  const parts = location.split(', ');
  return parts.length <= shown
    ? location
    : `${parts.slice(0, shown).join(', ')} +${parts.length - shown} more`;
}

/** e.g. "3d ago"; null when the source gave no date. */
export function formatPosted(p: JobPosting, now: Date): string | null {
  return p.postedAt ? `${shortDurationBetween(p.postedAt, now)} ago` : null;
}
