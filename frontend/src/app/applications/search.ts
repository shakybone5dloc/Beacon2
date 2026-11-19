import { ApplicationResponse, employerLabel } from './application.model';

/** Lower-case words without accents, so "Sr Java" finds "Sr. Java" and "Montréal" finds "montreal". */
export function searchWords(text: string): string[] {
  return plain(text)
    .split(/\s+/)
    .filter((w) => w.length > 0);
}

function plain(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase();
}

/** Every word appears in the employer, role, location, source or notes. */
export function matchesWords(app: ApplicationResponse, words: string[]): boolean {
  if (words.length === 0) return true;
  const haystack = plain(
    [employerLabel(app), app.role, app.location, app.source, app.notes].filter(Boolean).join(' '),
  );
  return words.every((word) => haystack.includes(word));
}
