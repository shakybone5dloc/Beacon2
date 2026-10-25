import { JobPosting, JobSource } from './posting.model';

export function aPosting(overrides: Partial<JobPosting> = {}): JobPosting {
  return {
    source: 'remotive',
    id: crypto.randomUUID(),
    title: 'Senior Java Engineer',
    company: 'Acme',
    location: 'USA',
    remote: true,
    url: `https://remotive.com/remote-jobs/${crypto.randomUUID()}`,
    postedAt: null,
    closesOn: null,
    salaryMin: null,
    salaryMax: null,
    salaryCurrency: null,
    salaryPeriod: null,
    salaryText: null,
    tags: [],
    snippet: null,
    description: null,
    ...overrides,
  };
}

export function aSource(overrides: Partial<JobSource> = {}): JobSource {
  return {
    id: 'remotive',
    name: 'Remotive',
    coverage: 'Remote jobs',
    attributionUrl: 'https://remotive.com',
    usesYourKey: false,
    ...overrides,
  };
}
