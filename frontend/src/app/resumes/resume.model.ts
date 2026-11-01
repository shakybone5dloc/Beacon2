import * as api from '../api/models';

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
