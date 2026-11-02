import * as api from '../api/models';
import { DocumentResponse } from '../documents/document.model';

/** Generated from the API; the names here are the ones the app uses. */
export type Resume = api.ResumeResponse;
export type UpdateResumeRequest = api.UpdateResumeRequest;

/** "Java, Spring Boot ,, Angular" -> ["Java", "Spring Boot", "Angular"]. */
export function splitList(text: string): string[] {
  return text
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

/** A resume to work from: one attached to this application, or one from the library. */
export interface ResumeChoice {
  id: string;
  label: string;
  isDefault: boolean;
}

/** This application's resumes first, then the library's (default first). */
export function resumeChoices(
  own: readonly DocumentResponse[],
  library: readonly Resume[],
): ResumeChoice[] {
  return [
    ...own
      .filter((d) => d.kind === 'RESUME')
      .map((d) => ({ id: d.id, label: `${d.filename} (this application)`, isDefault: false })),
    ...library.map((r) => ({
      id: r.documentId,
      label: r.isDefault ? `${r.label} (default)` : r.label,
      isDefault: r.isDefault,
    })),
  ];
}

/** Keeps the current pick when it's still there; otherwise the default, else the first. */
export function pickResume(choices: readonly ResumeChoice[], current: string): string {
  if (choices.some((r) => r.id === current)) return current;
  return (choices.find((r) => r.isDefault) ?? choices[0])?.id ?? '';
}
