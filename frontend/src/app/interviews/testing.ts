import { Interview } from './interview.model';

export function anInterview(overrides: Partial<Interview> = {}): Interview {
  return {
    id: 'i1',
    applicationId: 'a1',
    startsAt: '2099-03-10T19:00:00Z',
    endsAt: '2099-03-10T19:45:00Z',
    localStart: '2099-03-10T14:00:00',
    timeZone: 'America/Chicago',
    durationMinutes: 45,
    type: 'TECHNICAL',
    location: 'https://meet.example/abc',
    interviewers: [{ id: 'p1', name: 'Sam Ortiz', company: 'Acme' }],
    prepNotes: 'Review the payments design.',
    debriefNotes: null,
    outcome: null,
    version: 0,
    createdAt: '2026-10-28T12:00:00Z',
    updatedAt: '2026-10-28T12:00:00Z',
    ...overrides,
  };
}
