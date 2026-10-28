import { ApplicationResponse, PostingDetails } from './application.model';

export function anApplication(overrides: Partial<ApplicationResponse> = {}): ApplicationResponse {
  return {
    id: crypto.randomUUID(),
    companyId: 'c-acme',
    company: 'Acme',
    role: 'Engineer',
    jobUrl: null,
    notes: null,
    applyBy: null,
    followUpOn: null,
    location: null,
    remote: null,
    salaryMin: null,
    salaryMax: null,
    salaryCurrency: null,
    salaryPeriod: null,
    salaryText: null,
    source: null,
    postedOn: null,
    jobDescription: null,
    appliedAt: null,
    status: 'SAVED',
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    statusChangedAt: '2026-10-23T12:00:00Z',
    version: 0,
    allowedTransitions: [],
    ...overrides,
  };
}

/** Every posting field empty, for building requests in specs. */
export const noPostingDetails: PostingDetails = {
  location: null,
  remote: null,
  salaryMin: null,
  salaryMax: null,
  salaryCurrency: null,
  salaryPeriod: null,
  salaryText: null,
  source: null,
  postedOn: null,
  jobDescription: null,
};
