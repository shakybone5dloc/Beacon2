import { DocumentResponse } from './document.model';

export function aDocument(overrides: Partial<DocumentResponse> = {}): DocumentResponse {
  return {
    id: crypto.randomUUID(),
    applicationId: 'a1',
    kind: 'RESUME',
    filename: 'resume.pdf',
    contentType: 'application/pdf',
    sizeBytes: 84_000,
    createdAt: '2026-10-24T12:00:00Z',
    ...overrides,
  };
}
