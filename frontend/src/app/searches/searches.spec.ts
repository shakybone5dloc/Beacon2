import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { anApplication } from '../applications/testing';
import { aResume } from '../resumes/testing';
import { MatchesPage } from './matches-page';
import { SavedSearchesPage } from './saved-searches-page';
import { JobMatch, SavedSearch, scheduleLabel } from './search.model';

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
    snippet: 'Build services with Java.',
    score: null,
    applicationId: null,
    foundAt: '2026-11-02T12:00:00Z',
    ...overrides,
  };
}

function aSearch(overrides: Partial<SavedSearch> = {}): SavedSearch {
  return {
    id: 's1',
    name: 'Java remote',
    keywords: 'java',
    excludeWords: [],
    location: null,
    remoteOnly: true,
    salaryMin: null,
    sources: [],
    postedWithinDays: 7,
    resumeDocumentId: null,
    minScore: null,
    active: true,
    runDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
    runHour: 7,
    timeZone: 'America/New_York',
    nextRunAt: '2026-11-03T12:00:00Z',
    lastRunAt: null,
    running: false,
    newMatches: 0,
    lastRun: null,
    version: 0,
    createdAt: '2026-11-02T12:00:00Z',
    ...overrides,
  };
}

it('describes a schedule in words', () => {
  expect(scheduleLabel(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'], 7)).toBe(
    'Weekdays at 7 AM',
  );
  expect(scheduleLabel(['MONDAY', 'THURSDAY'], 13)).toBe('Mon, Thu at 1 PM');
  expect(
    scheduleLabel(
      ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'],
      0,
    ),
  ).toBe('Every day at 12 AM');
});

describe('saved searches and matches', () => {
  let http: HttpTestingController;

  function configure(queryParams: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  describe('MatchesPage', () => {
    async function open(matches: JobMatch[]) {
      configure();
      const fixture = TestBed.createComponent(MatchesPage);
      fixture.detectChanges();
      http
        .expectOne((r) => r.url === '/api/matches' && r.params.get('status') === 'NEW')
        .flush(matches);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('shows each match with its fit and the search that found it', async () => {
      const { el } = await open([
        aMatch({
          score: 88,
          salaryMin: 120000,
          salaryMax: 150000,
          salaryCurrency: 'USD',
          salaryPeriod: 'year',
        }),
      ]);

      const card = el.querySelector('.match')!;
      expect(card.querySelector('.title')?.getAttribute('href')).toBe('https://jobs.example/1');
      expect(card.querySelector('.fit')?.getAttribute('title')).toBe('Fit 88% · Strong fit');
      expect(card.textContent).toContain('$120k–$150k/yr');
      expect(card.textContent).toContain('Java remote');
    });

    it('saves a match to the board and links to it', async () => {
      const { fixture, el } = await open([aMatch()]);

      el.querySelector<HTMLButtonElement>('.save')!.click();
      http.expectOne('/api/matches/m1/save').flush(anApplication({ id: 'a9' }));
      http.expectOne('/api/matches/summary').flush({ newCount: 0 });
      await fixture.whenStable();

      expect(el.querySelector('.on-board')?.getAttribute('href')).toBe('/applications/a9');
      expect(el.querySelector('.save')).toBeNull();
    });

    it('dismisses a match off the list', async () => {
      const { fixture, el } = await open([
        aMatch(),
        aMatch({ id: 'm2', title: 'Kotlin Developer' }),
      ]);

      el.querySelector<HTMLButtonElement>('.dismiss')!.click();
      http.expectOne('/api/matches/m1/dismiss').flush(aMatch({ status: 'DISMISSED' }));
      http.expectOne('/api/matches/summary').flush({ newCount: 1 });
      await fixture.whenStable();

      expect(Array.from(el.querySelectorAll('.match .title')).map((a) => a.textContent)).toEqual([
        'Kotlin Developer',
      ]);
    });

    it('points to saved searches when nothing is waiting', async () => {
      const { el } = await open([]);

      expect(el.querySelector('.empty')?.textContent).toContain('Nothing waiting');
      expect(el.querySelector('.empty a')?.getAttribute('href')).toBe('/searches');
    });
  });

  describe('SavedSearchesPage', () => {
    async function open(searches: SavedSearch[], queryParams: Record<string, string> = {}) {
      configure(queryParams);
      const fixture = TestBed.createComponent(SavedSearchesPage);
      fixture.detectChanges();
      http.expectOne('/api/saved-searches').flush(searches);
      http.expectOne('/api/postings/sources').flush([
        { id: 'remotive', name: 'Remotive', coverage: '', attributionUrl: '', usesYourKey: false },
        {
          id: 'watchlist',
          name: 'Company watchlist',
          coverage: '',
          attributionUrl: '',
          usesYourKey: false,
        },
      ]);
      http
        .expectOne('/api/resumes')
        .flush([aResume({ documentId: 'r1', label: 'Backend', isDefault: true })]);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('lists each search with its schedule and last run', async () => {
      const { el } = await open([
        aSearch({
          resumeDocumentId: 'r1',
          minScore: 60,
          newMatches: 4,
          lastRun: {
            startedAt: '2026-11-02T12:00:00Z',
            finishedAt: '2026-11-02T12:01:00Z',
            gathered: 40,
            newMatches: 6,
            scored: 6,
            notes: ['Remotive: 40'],
          },
        }),
      ]);

      const card = el.querySelector('.search')!;
      expect(card.querySelector('.schedule')?.textContent).toContain('Weekdays at 7 AM');
      expect(card.querySelector('.schedule')?.textContent).toContain(
        'Fit-checked against Backend, hiding scores under 60',
      );
      expect(card.querySelector('.new-count')?.textContent?.trim()).toBe('4 new');
      expect(card.querySelector('.last-run summary')?.textContent).toContain(
        '40 found, 6 new, 6 fit-checked',
      );
      expect(card.querySelector('.last-run li')?.textContent).toBe('Remotive: 40');
    });

    it('starts a new search from Find jobs and saves it with the browser time zone', async () => {
      const { fixture, el } = await open([], {
        keywords: 'java spring',
        remote: 'true',
        sources: 'remotive',
      });

      expect(el.querySelector<HTMLInputElement>('input[formControlName=name]')!.value).toBe(
        'Java spring',
      );
      expect(
        el.querySelector<HTMLSelectElement>('select[formControlName=resumeDocumentId]')!.value,
      ).toBe('r1');
      el.querySelector('form.search-form')!.dispatchEvent(new Event('submit'));

      const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/saved-searches');
      expect(req.request.body).toEqual(
        expect.objectContaining({
          name: 'Java spring',
          keywords: 'java spring',
          remoteOnly: true,
          sources: ['remotive'],
          resumeDocumentId: 'r1',
          runDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
          runHour: 7,
          timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          version: null,
        }),
      );
      req.flush(aSearch({ name: 'Java spring' }), { status: 201, statusText: 'Created' });
      await fixture.whenStable();

      expect(el.querySelector('form.search-form')).toBeNull();
      expect(el.querySelector('.search .name')?.textContent).toBe('Java spring');
    });

    it('needs a day to run on', async () => {
      const { fixture, el } = await open([]);
      el.querySelector<HTMLButtonElement>('button.new')!.click();
      await fixture.whenStable();
      const keywords = el.querySelector<HTMLInputElement>('input[formControlName=keywords]')!;
      keywords.value = 'java';
      keywords.dispatchEvent(new Event('input'));
      el.querySelectorAll<HTMLButtonElement>('button.day').forEach((b) => {
        if (b.getAttribute('aria-pressed') === 'true') b.click();
      });
      el.querySelector('form.search-form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(el.querySelector('form.search-form')?.textContent).toContain('Pick at least one day');
    });

    it('runs a search now and checks back until it finishes', async () => {
      const { fixture, el } = await open([aSearch()]);
      vi.useFakeTimers();
      try {
        el.querySelector<HTMLButtonElement>('.run')!.click();
        http.expectOne('/api/saved-searches/s1/run').flush(aSearch({ running: true }), {
          status: 202,
          statusText: 'Accepted',
        });
        fixture.detectChanges();
        expect(el.querySelector('.running')?.textContent).toContain('Running now');

        vi.advanceTimersByTime(3000);
        http.expectOne('/api/saved-searches').flush([
          aSearch({
            newMatches: 2,
            lastRun: {
              startedAt: '2026-11-02T12:00:00Z',
              finishedAt: '2026-11-02T12:00:30Z',
              gathered: 10,
              newMatches: 2,
              scored: 0,
              notes: [],
            },
          }),
        ]);
        http.expectOne('/api/matches/summary').flush({ newCount: 2 });
        fixture.detectChanges();

        expect(el.querySelector('.running')).toBeNull();
        expect(el.querySelector('.new-count')?.textContent?.trim()).toBe('2 new');
      } finally {
        vi.useRealTimers();
      }
    });
  });
});
