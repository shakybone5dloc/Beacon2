import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { aDocument } from '../documents/testing';
import { ApplicationTimeline, TimelineEntry } from './application-timeline';

const empty = {
  statusChange: null,
  contact: null,
  interview: null,
  document: null,
  person: null,
};

const entries: TimelineEntry[] = [
  {
    ...empty,
    kind: 'INTERVIEW',
    at: '2099-03-10T19:00:00Z',
    interview: {
      id: 'i1',
      applicationId: 'a1',
      startsAt: '2099-03-10T19:00:00Z',
      endsAt: '2099-03-10T20:00:00Z',
      localStart: '2099-03-10T14:00:00',
      timeZone: 'America/Chicago',
      durationMinutes: 60,
      type: 'TECHNICAL',
      location: null,
      interviewers: [{ id: 'p2', name: 'Sam Ortiz', company: 'Acme' }],
      prepNotes: null,
      debriefNotes: null,
      outcome: null,
      version: 0,
      createdAt: '2026-10-28T12:00:00Z',
      updatedAt: '2026-10-28T12:00:00Z',
    },
  },
  {
    ...empty,
    kind: 'DOCUMENT',
    at: '2026-10-25T12:00:00Z',
    document: aDocument({ id: 'd1', applicationId: 'a1', kind: 'RESUME', filename: 'resume.pdf' }),
  },
  {
    ...empty,
    kind: 'CONTACT',
    at: '2026-10-22T12:00:00Z',
    contact: {
      id: 'c1',
      personId: 'p1',
      personName: 'Pat Lee',
      applicationId: 'a1',
      company: 'Acme',
      jobRole: 'Java Engineer',
      channel: 'LINKEDIN',
      direction: 'INBOUND',
      occurredAt: '2026-10-22T12:00:00Z',
      body: 'Hi!\n\n' + 'word '.repeat(60),
      version: 0,
      createdAt: '2026-10-22T12:00:00Z',
      updatedAt: '2026-10-22T12:00:00Z',
    },
  },
  {
    ...empty,
    kind: 'PERSON_ADDED',
    at: '2026-10-21T12:00:00Z',
    person: {
      role: 'RECRUITER',
      addedAt: '2026-10-21T12:00:00Z',
      person: {
        id: 'p1',
        name: 'Pat Lee',
        companyId: null,
        company: null,
        title: null,
        role: 'RECRUITER',
        recruiterType: null,
        email: null,
        phone: null,
        linkedinUrl: null,
        notes: null,
        version: 0,
        createdAt: '2026-10-21T12:00:00Z',
        updatedAt: '2026-10-21T12:00:00Z',
      },
    },
  },
  {
    ...empty,
    kind: 'STATUS_CHANGE',
    at: '2026-10-20T12:00:00Z',
    statusChange: { fromStatus: 'SAVED', toStatus: 'APPLIED', changedAt: '2026-10-20T12:00:00Z' },
  },
  {
    ...empty,
    kind: 'STATUS_CHANGE',
    at: '2026-10-19T12:00:00Z',
    statusChange: { fromStatus: null, toStatus: 'SAVED', changedAt: '2026-10-19T12:00:00Z' },
  },
];

describe('ApplicationTimeline', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(response: TimelineEntry[] | 'error') {
    const fixture = TestBed.createComponent(ApplicationTimeline);
    fixture.componentRef.setInput('applicationId', 'a1');
    fixture.detectChanges();
    const req = http.expectOne('/api/applications/a1/timeline');
    if (response === 'error') req.flush(null, { status: 500, statusText: 'Server Error' });
    else req.flush(response);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function texts(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('.timeline li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
  }

  it('describes each kind of event in the order the API gives', async () => {
    const { el } = await open(entries);

    const items = texts(el);
    expect(items.length).toBe(6);
    expect(items[0]).toContain('Technical interview with Sam Ortiz');
    expect(items[0]).toContain('Upcoming');
    expect(items[1]).toContain('Resume added: resume.pdf');
    expect(items[2]).toContain('LinkedIn from Pat Lee');
    expect(items[3]).toContain('Pat Lee joined as Recruiter');
    expect(items[4]).toContain('Moved to Applied');
    expect(items[5]).toContain('Added as Saved');
  });

  it('links to the people, the document and quotes a short snippet of a message', async () => {
    const { el } = await open(entries);

    expect(el.querySelector('.entry-person_added a')?.getAttribute('href')).toBe('/people/p1');
    expect(el.querySelector('.entry-document a')?.getAttribute('href')).toBe(
      '/api/applications/a1/documents/d1/content',
    );
    const snippet = el.querySelector('.snippet')!.textContent!.trim();
    expect(snippet.startsWith('“Hi! word')).toBe(true);
    expect(snippet.endsWith('…”')).toBe(true);
  });

  it('reloads when asked', async () => {
    const { fixture, el } = await open([]);
    expect(el.textContent).toContain('Nothing yet.');

    fixture.componentInstance.reload();
    http.expectOne('/api/applications/a1/timeline').flush(entries.slice(4));
    await fixture.whenStable();

    expect(texts(el).length).toBe(2);
  });

  it('says so when it cannot load', async () => {
    const { el } = await open('error');

    expect(el.textContent).toContain('Could not load the timeline.');
  });
});
