import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Tailoring = api.TailoringResponse;
export type ResumeSuggestion = api.ResumeSuggestionResponse;
export type SuggestionState = 'OPEN' | 'DONE' | 'DISMISSED';

export const KIND_LABELS: Record<string, string> = {
  ADD: 'Add',
  EMPHASIZE: 'Emphasize',
  REWORD: 'Reword',
  MOVE: 'Move',
  CUT: 'Cut',
};

/** Open ones first, then those done; dismissed ones are shown apart. */
export function splitSuggestions(list: readonly ResumeSuggestion[]): {
  active: ResumeSuggestion[];
  dismissed: ResumeSuggestion[];
} {
  const active = list.filter((s) => s.state !== 'DISMISSED');
  return {
    active: [
      ...active.filter((s) => s.state === 'OPEN'),
      ...active.filter((s) => s.state === 'DONE'),
    ],
    dismissed: list.filter((s) => s.state === 'DISMISSED'),
  };
}
