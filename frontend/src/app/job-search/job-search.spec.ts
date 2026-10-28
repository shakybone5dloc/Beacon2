import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { JobSearch } from './job-search';
import { aPosting, aSource } from '../postings/testing';
import { anApplication } from '../applications/testing';
import { JobPosting, JobSource } from '../postings/posting.model';
import { ApplicationResponse } from '../applications/application.model';

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

  async function render(board: ApplicationResponse[] = []) {
    const fixture = TestBed.createComponent(JobSearch);
    await fixture.whenStable();
    http.expectOne('/api/postings/sources').flush(sources);
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
    expect(el.querySelector('a.more')?.getAttribute('href')).toBe('/providers');
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
});
