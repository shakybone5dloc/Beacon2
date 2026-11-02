import { Resume } from './resume.model';

export function aResume(overrides: Partial<Resume> = {}): Resume {
  return {
    documentId: 'r1',
    label: 'Backend Java',
    isDefault: true,
    filename: 'backend.pdf',
    contentType: 'application/pdf',
    sizeBytes: 1000,
    textStatus: 'READ',
    targetTitles: [],
    skills: [],
    yearsExperience: null,
    version: 0,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}
