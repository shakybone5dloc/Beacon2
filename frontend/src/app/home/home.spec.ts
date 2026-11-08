import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ApplicationResponse } from '../applications/application.model';
import { DAY } from '../applications/durations';
import { anApplication } from '../applications/testing';
import { JobMatch } from '../searches/search.model';
import { Stats } from '../stats/stats.model';
import { Deck } from '../study/study.model';
import { oldBoardLinks, routes } from '../app.routes';
import { HomePage } from './home-page';
import {
  greeting,
  HomeTodos,
  queue,
  sentThisWeek,
  stageCounts,
  upcomingInterviews,
} from './home.model';

const NOW = new Date();
const daysAgo = (days: number) => new Date(NOW.getTime() - days * DAY - 60_000).toISOString();
const dateIn = (days: number) => {
  const d = new Date(NOW.getTime() + days * DAY);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** An application with nothing due: changed just now, so not gone quiet. */
function quiet(overrides: Partial<ApplicationResponse> = {}): ApplicationResponse {
  return anApplication({ statusChangedAt: NOW.toISOString(), ...overrides });
}

function aMatch(overrides: Partial<JobMatch> = {}): JobMatch {
  return {
    id: 'm1',
    status: 'NEW',
    searchId: 's1',
    searchName: 'Java remote',
    source: 'remotive',
    title: 'Java Developer',
    company: 'Initech',
    location: 'USA',
    remote: true,
    url: 'https://jobs.example/1',
    postedAt: null,
    closesOn: null,
    salaryMin: null,
    salaryMax: null,
    salaryCurrency: null,
    salaryPeriod: null,
    salaryText: null,
    snippet: null,
    score: null,
    applicationId: null,
    foundAt: '2026-11-02T12:00:00Z',
    ...overrides,
  };
}

function aDeck(overrides: Partial<Deck> = {}): Deck {
  return {
    id: 'd1',
    name: 'GHX – Backend Engineer',
    applicationId: 'a1',
    applicationLabel: 'GHX – Backend Engineer',
    cards: 4,
    due: 3,
    drafts: 0,
    nextDueAt: daysAgo(1),
    version: 0,
    createdAt: daysAgo(5),
    updatedAt: daysAgo(5),
    ...overrides,
  };
}

function someStats(): Stats {
  return {
    since: null,
    overall: {
      name: null,
      total: 10,
      applied: 8,
      responded: 2,
      interviewed: 1,
      offered: 0,
      ghosted: 1,
      medianDaysToResponse: null,
    },
    bySource: [],
    byResume: [],
    stages: [],
    weeks: [
      { weekStart: '2026-10-26', applied: 4 },
      { weekStart: '2026-11-02', applied: 2 },
    ],
  };
}

const NO_TODOS: HomeTodos = { missingSentResume: [], openSuggestions: [] };

describe('Home helpers', () => {
  it('greets by the time of day and first name', () => {
    expect(greeting(new Date(2026, 10, 7, 8), 'Aaron Keiper')).toBe('Good morning, Aaron');
    expect(greeting(new Date(2026, 10, 7, 13), null)).toBe('Good afternoon');
    expect(greeting(new Date(2026, 10, 7, 20), '  ')).toBe('Good evening');
  });

  it('lists interviews in the next two weeks, soonest first, with their cards due', () => {
    const at = (days: number) => new Date(NOW.getTime() + days * DAY).toISOString();
    const list = upcomingInterviews(
      [
        quiet({ id: 'later', nextInterviewAt: at(5) }),
        quiet({ id: 'a1', nextInterviewAt: at(1) }),
        quiet({ id: 'far', nextInterviewAt: at(20) }),
        quiet({ id: 'none' }),
      ],
      [aDeck()],
      NOW,
    );
    expect(list.map((u) => u.app.id)).toEqual(['a1', 'later']);
    expect(list[0].cardsDue).toBe(3);
    expect(list[1].deckId).toBeNull();
  });

  it('queues the best-fitting new matches first, a few at most', () => {
    const ids = queue([
      aMatch({ id: 'unscored' }),
      aMatch({ id: 'good', score: 75 }),
      aMatch({ id: 'best', score: 90 }),
      ...Array.from({ length: 5 }, (_, i) => aMatch({ id: `m${i}`, score: 10 })),
    ]).map((m) => m.id);
    expect(ids.slice(0, 2)).toEqual(['best', 'good']);
    expect(ids.length).toBe(5);
    expect(ids).not.toContain('unscored');
  });

  it('counts this week, last week and each active stage', () => {
    expect(sentThisWeek(someStats().weeks)).toEqual({ thisWeek: 2, lastWeek: 4 });
    expect(sentThisWeek([])).toEqual({ thisWeek: 0, lastWeek: 0 });
    expect(
      stageCounts([
        quiet({ status: 'APPLIED' }),
        quiet({ status: 'APPLIED' }),
        quiet({ status: 'REJECTED' }),
      ]),
    ).toEqual([
      { status: 'SAVED', label: 'Saved', count: 0 },
      { status: 'APPLIED', label: 'Applied', count: 2 },
      { status: 'INTERVIEWING', label: 'Interviewing', count: 0 },
      { status: 'OFFER', label: 'Offer', count: 0 },
    ]);
  });
});

describe('HomePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(
    data: {
      apps?: ApplicationResponse[];
      matches?: JobMatch[];
      decks?: Deck[];
      todos?: HomeTodos;
    } = {},
  ) {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/applications').flush(data.apps ?? []);
    http.expectOne((r) => r.url === '/api/matches').flush(data.matches ?? []);
    http.expectOne('/api/study/decks').flush(data.decks ?? []);
    http.expectOne((r) => r.url === '/api/stats').flush(someStats());
    http.expectOne('/api/home/todos').flush(data.todos ?? NO_TODOS);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it("says you're all caught up when nothing needs you", async () => {
    const { el } = await open({ apps: [quiet({ status: 'APPLIED' })] });

    expect(el.querySelector('.caught-up')).toBeTruthy();
    expect(el.querySelector('.attention')).toBeNull();
    expect(el.querySelector('.queue')).toBeNull();
    expect(el.querySelector('.this-week .sent')?.textContent?.trim()).toBe('2');
    expect(el.querySelector('.this-week .response')?.textContent?.trim()).toBe('25%');
  });

  it('lists what needs attention, most urgent first, and clears a follow-up with Done', async () => {
    const due = quiet({
      id: 'a1',
      company: 'Globex',
      status: 'INTERVIEWING',
      version: 2,
      followUpOn: dateIn(0),
    });
    const { fixture, el } = await open({
      apps: [
        quiet({ id: 'a2', company: 'Acme', status: 'APPLIED', statusChangedAt: daysAgo(8) }),
        due,
        quiet({ id: 'a3', company: 'Initech', status: 'SAVED', applyBy: dateIn(1) }),
      ],
    });

    const items = Array.from(el.querySelectorAll('.attention li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(items[0]).toContain('Globex');
    expect(items[0]).toContain('Follow up today');
    expect(items[1]).toContain('Initech');
    expect(items[2]).toContain('No movement in 8 days');
    expect(el.querySelector('.caught-up')).toBeNull();

    el.querySelector<HTMLButtonElement>('.attention button')!.click();
    const req = http.expectOne('/api/applications/a1/follow-up');
    expect(req.request.body).toEqual({ followUpOn: null, version: 2 });
    req.flush({ ...due, followUpOn: null, version: 3 });
    await fixture.whenStable();

    expect(el.querySelectorAll('.attention li').length).toBe(2);
  });

  it('shows interviews coming up with their brief and cards to study', async () => {
    const tomorrow = new Date(NOW.getTime() + DAY).toISOString();
    const { el } = await open({
      apps: [
        quiet({ id: 'a1', company: 'GHX', status: 'INTERVIEWING', nextInterviewAt: tomorrow }),
      ],
      decks: [aDeck()],
    });

    const interview = el.querySelector('.upcoming .interview')!;
    expect(interview.textContent).toContain('GHX');
    expect(interview.querySelector('a[href="/applications/a1/brief"]')).toBeTruthy();
    expect(interview.querySelector('.study-due')?.getAttribute('href')).toBe('/study/d1/session');
    expect(interview.querySelector('.study-due')?.textContent).toContain('Study 3 cards');
    expect(el.querySelector('.study')?.textContent).toContain('3 cards are due');
  });

  it('saves or dismisses matches from the queue', async () => {
    const { fixture, el } = await open({
      matches: [aMatch({ id: 'm1', score: 88 }), aMatch({ id: 'm2', title: 'Kotlin Dev' })],
    });

    expect(el.querySelectorAll('.queue .match').length).toBe(2);
    expect(el.querySelector('.queue .pill')?.textContent).toContain('88 · Strong fit');

    el.querySelector<HTMLButtonElement>('.queue .dismiss')!.click();
    http.expectOne('/api/matches/m1/dismiss').flush(aMatch({ id: 'm1', status: 'DISMISSED' }));
    http.expectOne('/api/matches/summary').flush({ newCount: 1 });
    await fixture.whenStable();

    expect(el.querySelectorAll('.queue .match').length).toBe(1);
    el.querySelector<HTMLButtonElement>('.queue .save')!.click();
    http.expectOne('/api/matches/m2/save').flush(quiet({ id: 'a9', company: 'Initech' }));
    http.expectOne('/api/matches/summary').flush({ newCount: 0 });
    await fixture.whenStable();

    expect(el.querySelector('.queue')).toBeNull();
  });

  it('lists to-dos for resumes', async () => {
    const { el } = await open({
      todos: {
        missingSentResume: [{ applicationId: 'a1', employer: 'GHX', role: 'Backend', count: 1 }],
        openSuggestions: [{ applicationId: 'a2', employer: 'Initech', role: 'Platform', count: 2 }],
      },
    });

    expect(el.querySelector('.missing-resume')?.textContent).toContain(
      'Record which resume you sent to',
    );
    expect(el.querySelector('.open-suggestions')?.textContent).toContain(
      '2 resume suggestions to go',
    );
    expect(el.querySelector('.caught-up')).toBeNull();
  });

  it('says when part of Home could not load, and shows the rest', async () => {
    const fixture = TestBed.createComponent(HomePage);
    fixture.detectChanges();
    http.expectOne('/api/applications').flush([]);
    http
      .expectOne((r) => r.url === '/api/matches')
      .flush('down', { status: 500, statusText: 'Server Error' });
    http.expectOne('/api/study/decks').flush([]);
    http.expectOne((r) => r.url === '/api/stats').flush(someStats());
    http.expectOne('/api/home/todos').flush(NO_TODOS);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.notice')?.textContent).toContain("Some of Home couldn't load");
    expect(el.querySelector('.this-week')).toBeTruthy();
    expect(el.querySelector('.caught-up')).toBeNull();
  });
});

describe('old board links', () => {
  it('sends a bookmarked board filter to the board and leaves plain Home alone', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() =>
      oldBoardLinks(
        { queryParams: { q: 'java', remote: 'true', other: 'x' } } as never,
        {} as never,
      ),
    );
    expect(router.serializeUrl(result as never)).toBe('/board?q=java&remote=true');
    expect(
      TestBed.runInInjectionContext(() => oldBoardLinks({ queryParams: {} } as never, {} as never)),
    ).toBe(true);
  });
});
