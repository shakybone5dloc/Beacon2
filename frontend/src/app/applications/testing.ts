import { ApplicationResponse } from './application.model';

export function anApplication(overrides: Partial<ApplicationResponse> = {}): ApplicationResponse {
  return {
    id: crypto.randomUUID(),
    company: 'Acme',
    role: 'Engineer',
    jobUrl: null,
    notes: null,
    status: 'SAVED',
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    version: 0,
    allowedTransitions: [],
    ...overrides,
  };
}
