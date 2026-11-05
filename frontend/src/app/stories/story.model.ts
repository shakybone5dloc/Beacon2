import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Story = api.StoryResponse;
export type StoryDraft = api.StoryDraftResponse;
export type SaveStoryRequest = api.SaveStoryRequest;

/** The parts of a STAR story, in the order they're told. */
export const STAR_PARTS = [
  { key: 'situation', label: 'Situation', hint: 'Where and when; what was going on.' },
  { key: 'task', label: 'Task', hint: 'What you had to do, and why it mattered.' },
  { key: 'action', label: 'Action', hint: 'What you did yourself: choices and trade-offs.' },
  { key: 'result', label: 'Result', hint: 'How it turned out.' },
] as const;

export type StarPart = (typeof STAR_PARTS)[number]['key'];

/** "leadership, Hardest problem ," -> ["leadership", "hardest problem"]; the server cleans them the same way. */
export function tagsFromText(text: string): string[] {
  return [
    ...new Set(
      text
        .split(',')
        .map((tag) => tag.trim().toLowerCase())
        .filter((tag) => tag.length > 0),
    ),
  ];
}
