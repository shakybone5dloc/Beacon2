import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Fit = api.FitResponse;
export type ScorePostingFitRequest = api.ScorePostingFitRequest;

export interface Verdict {
  label: string;
  /** A pill class from styles.css. */
  pill: 'pill-green' | 'pill-blue' | 'pill-amber' | 'pill-red';
}

/** The bands the prompt scores against. */
export function verdict(score: number): Verdict {
  if (score >= 85) return { label: 'Strong fit', pill: 'pill-green' };
  if (score >= 70) return { label: 'Good fit', pill: 'pill-blue' };
  if (score >= 50) return { label: 'Stretch', pill: 'pill-amber' };
  return { label: 'Poor fit', pill: 'pill-red' };
}

/**
 * The resume's skills that a posting mentions, matched as whole words and ignoring
 * case: a free first look before paying for an AI check.
 */
export function matchingSkills(skills: readonly string[], text: string): string[] {
  const haystack = text.toLowerCase();
  return skills.filter((skill) => {
    const needle = skill.trim().toLowerCase();
    if (!needle) return false;
    // Word edges that also work for skills like "C#", "C++" and ".NET".
    const escaped = needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`(^|[^a-z0-9])${escaped}(?=$|[^a-z0-9])`).test(haystack);
  });
}
