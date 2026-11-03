import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { JobSearch } from './job-search';
import { aPosting, aSource } from '../postings/testing';
import { anApplication } from '../applications/testing';
import { JobPosting, JobSource } from '../postings/posting.model';
import { ApplicationResponse } from '../applications/application.model';
import { Resume } from '../resumes/resume.model';
import { aResume } from '../resumes/testing';
import { aFit } from '../fit/testing';
import { AuthState } from '../auth/auth-state';

describe('JobSearch', () => {
  let http: HttpTestingController;

  const sources: JobSource[] = [
    aSource({ id: 'remotive', name: 'Remotive' }),
    aSource({ id: 'jobicy', name: 'Jobicy', attributionUrl: 'https://jobicy.com' }),
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [JobSearch],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(
    board: ApplicationResponse[] = [],
    fit: { configured: boolean; resumes: Resume[] } = { configured: false, resumes: [] },
  ) {
    const fixture = TestBed.createComponent(JobSearch);
    await fixture.whenStable();
    http.expectOne('/api/postings/sources').flush(sources);
    http.expectOne('/api/fit/status').flush({ configured: fit.configured });
    http.expectOne('/api/resumes').flush(fit.resumes);
    http.expectOne('/api/applications').flush(board);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function fill(el: HTMLElement, name: string, value: string) {
    const input = el.querySelector<HTMLInputElement>(`input[formControlName=${name}]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function sourceBox(el: HTMLElement, name: string): HTMLInputElement {
    return Array.from(el.querySelectorAll<HTMLLabelElement>('label.source'))
      .find((l) => l.textContent?.includes(name))!
      .querySelector('input')!;
  }

  async function searchFor(
    fixture: Awaited<ReturnType<typeof render>>['fixture'],
    postings: JobPosting[],
    sourceResults = [{ id: 'remotive', count: postings.length, error: null as string | null }],
  ) {
    const el: HTMLElement = fixture.nativeElement;
    fill(el, 'keywords', 'java');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const req = http.expectOne((r) => r.url === '/api/postings/search');
    req.flush({ postings, sources: sourceResults });
    await fixture.whenStable();
    return req;
  }

  it('lists the sources the server says this user can search, all ticked', async () => {
    const { el } = await render();

    expect(sourceBox(el, 'Remotive').checked).toBe(true);
    expect(sourceBox(el, 'Jobicy').checked).toBe(true);
    expect(el.querySelectorAll('label.source').length).toBe(2);
    const more = Array.from(el.querySelectorAll('.more a')).map((a) => a.getAttribute('href'));
    expect(more).toEqual(['/providers', '/companies']);
  });

  it('does not search without keywords', async () => {
    const { fixture, el } = await render();

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    http.expectNone((r) => r.url === '/api/postings/search');
    expect(el.textContent).toContain('Enter at least one keyword.');
  });

  it('searches only the ticked sources', async () => {
    const { fixture, el } = await render();
    sourceBox(el, 'Jobicy').click();
    fill(el, 'location', 'USA');

    const req = await searchFor(fixture, []);

    expect(req.request.params.get('keywords')).toBe('java');
    expect(req.request.params.get('location')).toBe('USA');
    expect(req.request.params.get('sources')).toBe('remotive');
  });

  it('shows that it is searching until the results arrive', async () => {
    const { fixture, el } = await render();
    fill(el, 'keywords', 'java');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    const searching = el.querySelector('.searching');
    expect(searching?.getAttribute('role')).toBe('status');
    expect(searching?.querySelectorAll('.loading-dots span').length).toBe(3);
    expect(searching?.textContent).toContain('Searching 2 sources...');
    expect(searching?.textContent).toContain('a minute or two');

    http
      .expectOne((r) => r.url === '/api/postings/search')
      .flush({ postings: [], sources: [{ id: 'remotive', count: 0, error: null }] });
    await fixture.whenStable();

    expect(el.querySelector('.searching')).toBeNull();
    expect(el.querySelector('.results')).not.toBeNull();
  });

  describe('remembered options', () => {
    function signIn(id: string) {
      TestBed.inject(AuthState)
        .login({ email: `${id}@example.com`, password: 'x' })
        .subscribe();
      http.expectOne('/api/auth/csrf').flush(null);
      http
        .expectOne('/api/auth/login')
        .flush({ id, email: `${id}@example.com`, displayName: null });
    }

    afterEach(() => localStorage.clear());

    it('keeps the options and unticked sources in this browser as they change', async () => {
      signIn('u1');
      const { fixture, el } = await render();

      fill(el, 'keywords', 'java spring');
      fill(el, 'location', 'Denver');
      el.querySelector<HTMLInputElement>('input[formControlName=remoteOnly]')!.click();
      sourceBox(el, 'Jobicy').click();
      await fixture.whenStable();

      expect(JSON.parse(localStorage.getItem('beacon.jobSearch.u1')!)).toEqual({
        keywords: 'java spring',
        location: 'Denver',
        remoteOnly: true,
        excludedSources: ['jobicy'],
      });
    });

    it('fills the form from what this user had last time', async () => {
      localStorage.setItem(
        'beacon.jobSearch.u1',
        JSON.stringify({
          keywords: 'kotlin',
          location: 'USA',
          remoteOnly: true,
          excludedSources: ['jobicy'],
        }),
      );
      localStorage.setItem(
        'beacon.jobSearch.u2',
        JSON.stringify({ keywords: 'cobol', location: '', remoteOnly: false, excludedSources: [] }),
      );
      signIn('u1');
      const { el } = await render();

      expect(el.querySelector<HTMLInputElement>('input[formControlName=keywords]')!.value).toBe(
        'kotlin',
      );
      expect(el.querySelector<HTMLInputElement>('input[formControlName=location]')!.value).toBe(
        'USA',
      );
      expect(el.querySelector<HTMLInputElement>('input[formControlName=remoteOnly]')!.checked).toBe(
        true,
      );
      expect(sourceBox(el, 'Remotive').checked).toBe(true);
      expect(sourceBox(el, 'Jobicy').checked).toBe(false);
    });

    it('starts blank when what was saved is damaged', async () => {
      localStorage.setItem('beacon.jobSearch.u1', '{not json');
      signIn('u1');
      const { el } = await render();

      expect(el.querySelector<HTMLInputElement>('input[formControlName=keywords]')!.value).toBe('');
      expect(sourceBox(el, 'Jobicy').checked).toBe(true);
    });
  });

  it('shows postings with their source credited and linked', async () => {
    const { fixture, el } = await render();

    await searchFor(fixture, [
      aPosting({
        title: 'Senior Java Engineer',
        company: 'Acme',
        url: 'https://remotive.com/remote-jobs/1',
        salaryMin: 150000,
        salaryMax: 180000,
        salaryCurrency: 'USD',
        salaryPeriod: 'year',
        closesOn: '2026-11-21',
        snippet: 'Build things',
      }),
    ]);

    const posting = el.querySelector('.posting')!;
    expect(posting.querySelector('a.title')?.getAttribute('href')).toBe(
      'https://remotive.com/remote-jobs/1',
    );
    expect(posting.textContent).toContain('Acme');
    expect(posting.textContent).toContain('$150k–$180k/yr');
    expect(posting.textContent).toContain('Closes Nov 21');
    expect(posting.textContent).toContain('Build things');
    const via = posting.querySelector('a.via')!;
    expect(via.textContent?.trim()).toBe('via Remotive');
    expect(via.getAttribute('href')).toBe('https://remotive.com');
  });

  it('reports a source that failed', async () => {
    const { fixture, el } = await render();

    await searchFor(fixture, [], [{ id: 'jobicy', count: 0, error: 'Could not reach Jobicy.' }]);

    expect(el.querySelector('.notice')?.textContent).toContain('Jobicy: Could not reach Jobicy.');
  });

  it('saves a posting to the board with its closing date as apply-by', async () => {
    const { fixture, el } = await render();
    const posting = aPosting({
      title: 'Senior Java Engineer',
      company: 'Acme',
      url: 'https://remotive.com/remote-jobs/1',
      closesOn: '2026-11-21',
    });
    await searchFor(fixture, [posting]);

    el.querySelector<HTMLButtonElement>('button.save')!.click();

    const req = http.expectOne('/api/applications');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      companyName: 'Acme',
      role: 'Senior Java Engineer',
      jobUrl: 'https://remotive.com/remote-jobs/1',
      notes: null,
      applyBy: '2026-11-21',
      followUpOn: null,
      location: 'USA',
      remote: true,
      salaryMin: null,
      salaryMax: null,
      salaryCurrency: null,
      salaryPeriod: null,
      salaryText: null,
      source: 'Remotive',
      postedOn: null,
      jobDescription: null,
    });
    req.flush(anApplication({ id: 'new-app' }));
    await fixture.whenStable();

    const saved = el.querySelector('a.saved')!;
    expect(saved.textContent).toContain('On your board');
    expect(saved.getAttribute('href')).toBe('/applications/new-app');
  });

  it('saves a posting without a company as an undisclosed employer', async () => {
    const { fixture, el } = await render();
    await searchFor(fixture, [aPosting({ title: 'Java Contractor', company: null })]);

    el.querySelector<HTMLButtonElement>('button.save')!.click();

    const req = http.expectOne('/api/applications');
    expect(req.request.body.companyName).toBeNull();
    req.flush(anApplication({ company: null, companyId: null }));
    await fixture.whenStable();
  });

  it('saves the full description and posting details when the source has them', async () => {
    const { fixture, el } = await render();
    await searchFor(fixture, [
      aPosting({
        source: 'remotive',
        location: 'United States, Canada',
        salaryMin: 140000,
        salaryMax: 170000,
        salaryCurrency: 'USD',
        salaryPeriod: 'weekly',
        postedAt: '2026-10-20T08:00:00Z',
        description: 'About us\n\nWe build tools.',
      }),
    ]);

    el.querySelector<HTMLButtonElement>('button.save')!.click();

    const req = http.expectOne('/api/applications');
    expect(req.request.body).toMatchObject({
      location: 'United States, Canada',
      salaryMin: 140000,
      salaryMax: 170000,
      salaryCurrency: 'USD',
      // "weekly" isn't a period the board stores, so it is dropped rather than failing the save.
      salaryPeriod: null,
      source: 'Remotive',
      postedOn: '2026-10-20',
      jobDescription: 'About us\n\nWe build tools.',
    });
    req.flush(anApplication());
  });

  it('marks postings that are already on the board', async () => {
    const { fixture, el } = await render([
      anApplication({ id: 'existing', jobUrl: 'https://remotive.com/remote-jobs/1' }),
    ]);

    await searchFor(fixture, [aPosting({ url: 'https://remotive.com/remote-jobs/1' })]);

    expect(el.querySelector('button.save')).toBeNull();
    expect(el.querySelector('a.saved')?.getAttribute('href')).toBe('/applications/existing');
  });

  describe('fit', () => {
    const withSkills = aResume({ skills: ['Java', 'Spring Boot', 'Kubernetes', 'C#'] });

    it("shows how many of the default resume's skills a posting mentions, for free", async () => {
      const { fixture, el } = await render([], { configured: false, resumes: [withSkills] });

      await searchFor(fixture, [
        aPosting({ title: 'Java Engineer', snippet: 'Spring Boot services', tags: ['javascript'] }),
      ]);

      const chip = el.querySelector('.skills')!;
      expect(chip.textContent?.trim()).toBe('2/4 skills');
      expect(chip.getAttribute('title')).toBe('Your skills it mentions: Java, Spring Boot');
      // Without an AI key there's nothing to check with.
      expect(el.querySelector('button.check-fit')).toBeNull();
    });

    it('checks a result against the default resume and shows the score', async () => {
      const { fixture, el } = await render([], { configured: true, resumes: [withSkills] });
      await searchFor(fixture, [
        aPosting({ title: 'Java Engineer', company: 'Acme', description: '  Build services.  ' }),
      ]);

      el.querySelector<HTMLButtonElement>('button.check-fit')!.click();
      const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/fit');
      expect(req.request.body).toEqual({
        resumeDocumentId: 'r1',
        title: 'Java Engineer',
        company: 'Acme',
        description: 'Build services.',
      });
      req.flush(aFit({ score: 88 }));
      await fixture.whenStable();

      expect(el.querySelector('.fit .score')?.textContent).toBe('88');
      expect(el.querySelector('.fit .verdict')?.textContent).toBe('Strong fit');
      expect(el.querySelector('button.check-fit')).toBeNull();
    });

    it('shows why a check failed', async () => {
      const { fixture, el } = await render([], { configured: true, resumes: [withSkills] });
      await searchFor(fixture, [aPosting({ snippet: 'Java' })]);

      el.querySelector<HTMLButtonElement>('button.check-fit')!.click();
      http
        .expectOne('/api/fit')
        .flush(
          { detail: 'Your OpenAI account is out of credits.' },
          { status: 422, statusText: 'Unprocessable' },
        );
      await fixture.whenStable();

      expect(el.querySelector('.posting .error')?.textContent).toContain('out of credits');
    });

    it('gives a saved result the fit already checked', async () => {
      const { fixture, el } = await render([], { configured: true, resumes: [withSkills] });
      await searchFor(fixture, [aPosting({ description: 'Build services.' })]);
      el.querySelector<HTMLButtonElement>('button.check-fit')!.click();
      http.expectOne('/api/fit').flush(aFit());
      await fixture.whenStable();

      el.querySelector<HTMLButtonElement>('button.save')!.click();
      http.expectOne('/api/applications').flush(anApplication({ id: 'new-app' }));
      await fixture.whenStable();

      const link = http.expectOne('/api/applications/new-app/fit');
      expect(link.request.body).toEqual({ resumeDocumentId: 'r1' });
      link.flush(aFit());
    });

    it('cannot check a result that has no text, or with an unreadable resume', async () => {
      const { fixture, el } = await render([], {
        configured: true,
        resumes: [aResume({ textStatus: 'UNREADABLE' })],
      });
      await searchFor(fixture, [aPosting({ description: 'Java' })]);

      expect(el.querySelector('button.check-fit')).toBeNull();
    });
  });
});
