import { Deck } from './study.model';

const PAST = '2026-01-01T00:00:00Z';

export function aDeck(overrides: Partial<Deck> = {}): Deck {
  return {
    id: 'd1',
    name: 'GHX – Senior Java Engineer',
    kind: 'STANDARD',
    applicationId: 'a1',
    applicationLabel: 'GHX – Senior Java Engineer',
    cards: 2,
    due: 1,
    drafts: 0,
    nextDueAt: PAST,
    version: 0,
    createdAt: PAST,
    updatedAt: PAST,
    ...overrides,
  };
}
