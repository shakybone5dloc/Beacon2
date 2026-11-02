import { Fit } from './fit.model';

export function aFit(overrides: Partial<Fit> = {}): Fit {
  return {
    id: 'fit-1',
    score: 82,
    matchedSkills: ['Java', 'Spring Boot'],
    gaps: ['Kubernetes'],
    dealbreakers: [],
    rationale: 'Strong Java background; no Kubernetes shown.',
    applyBy: null,
    resumeDocumentId: 'r1',
    resumeLabel: 'Backend Java',
    scoredAt: '2026-11-01T12:00:00Z',
    current: true,
    ...overrides,
  };
}
