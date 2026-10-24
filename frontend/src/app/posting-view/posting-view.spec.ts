import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { PostingView } from './posting-view';
import { PostingPanel } from './posting-panel';
import { PrintablePosting } from './printable-posting';
import { anApplication } from '../applications/testing';
import { ApplicationResponse } from '../applications/application.model';
import { aDocument } from '../documents/testing';

const fullPosting = anApplication({
  id: 'a1',
  company: 'Acme',
  role: 'Senior Java Engineer',
  jobUrl: 'https://acme.dev/jobs/1',
  location: 'Denver, CO',
  remote: true,
  salaryMin: 140000,
  salaryMax: 170000,
  salaryCurrency: 'USD',
  salaryPeriod: 'year',
  postedOn: '2026-10-20',
  applyBy: '2026-11-15',
  source: 'Himalayas',
  jobDescription: 'About us\n\nWe build tools.\n• Java\n• Spring',
});

describe('PostingView', () => {
  async function render(app: ApplicationResponse) {
    TestBed.configureTestingModule({ imports: [PostingView] });
    const fixture = TestBed.createComponent(PostingView);
    fixture.componentRef.setInput('application', app);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lays out the stored posting like a job ad', async () => {
    const el = await render(fullPosting);

    expect(el.querySelector('h1')?.textContent).toBe('Senior Java Engineer');
    expect(el.querySelector('.company')?.textContent).toBe('Acme');
    const facts = Array.from(el.querySelectorAll('.facts div')).map(
      (d) =>
        `${d.querySelector('dt')!.textContent!.trim()}: ${d.querySelector('dd')!.textContent!.replace(/\s+/g, ' ').trim()}`,
    );
    expect(facts).toEqual([
      'Location: Denver, CO Remote',
      'Salary: $140k–$170k/yr',
      'Posted: Oct 20, 2026',
      'Apply by: Nov 15, 2026',
      'Found on: Himalayas',
    ]);
    expect(el.querySelector('.original a')?.getAttribute('href')).toBe('https://acme.dev/jobs/1');
    const paragraphs = Array.from(el.querySelectorAll('.description p')).map((p) => p.textContent);
    expect(paragraphs).toEqual(['About us', 'We build tools.\n• Java\n• Spring']);
  });

  it('says when there is no description and skips facts it does not have', async () => {
    const el = await render(anApplication({ company: 'Acme', role: 'Engineer' }));

    expect(el.querySelectorAll('.facts div').length).toBe(0);
    expect(el.querySelector('.empty')?.textContent).toContain('No job description saved yet');
    expect(el.querySelector('.original')).toBeNull();
  });
});

describe('PostingPanel', () => {
  async function render(app: ApplicationResponse, pdfUrl: string | null = null) {
    TestBed.configureTestingModule({ imports: [PostingPanel], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(PostingPanel);
    fixture.componentRef.setInput('application', app);
    fixture.componentRef.setInput('postingPdfUrl', pdfUrl);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function tabs(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('[role=tab]')).map((t) => t.textContent!.trim());
  }

  function clickTab(el: HTMLElement, label: string) {
    Array.from(el.querySelectorAll<HTMLButtonElement>('[role=tab]'))
      .find((t) => t.textContent?.trim() === label)!
      .click();
  }

  it("opens on Beacon's own view with a link to the printable page", async () => {
    const { el } = await render(fullPosting);

    expect(el.querySelector('app-posting-view')).not.toBeNull();
    expect(el.querySelector('a.print')?.getAttribute('href')).toBe('/applications/a1/posting');
  });

  it('only offers the tabs it has content for', async () => {
    expect(tabs((await render(anApplication({ jobUrl: null }))).el)).toEqual(['Posting']);
    TestBed.resetTestingModule();
    expect(
      tabs((await render(fullPosting, '/api/applications/a1/documents/d1/content')).el),
    ).toEqual(['Posting', 'Saved PDF', 'Live page']);
  });

  it('shows the saved PDF from Beacon in a frame', async () => {
    const { fixture, el } = await render(fullPosting, '/api/applications/a1/documents/d1/content');

    clickTab(el, 'Saved PDF');
    await fixture.whenStable();

    expect(el.querySelector('iframe')?.getAttribute('src')).toBe(
      '/api/applications/a1/documents/d1/content',
    );
  });

  it('frames the live page with a way out for sites that refuse', async () => {
    const { fixture, el } = await render(fullPosting);

    clickTab(el, 'Live page');
    await fixture.whenStable();

    const frame = el.querySelector('iframe')!;
    expect(frame.getAttribute('src')).toBe('https://acme.dev/jobs/1');
    expect(frame.getAttribute('sandbox')).toContain('allow-scripts');
    expect(el.querySelector('.note a')?.getAttribute('href')).toBe('https://acme.dev/jobs/1');
  });

  it('never frames a non-web job URL', async () => {
    const { el } = await render(anApplication({ jobUrl: 'javascript:alert(1)' }));

    expect(tabs(el)).toEqual(['Posting']);
  });

  it("never frames a page on Beacon's own origin", async () => {
    const { el } = await render(
      anApplication({ jobUrl: `${window.location.origin}/applications/x` }),
    );

    expect(tabs(el)).toEqual(['Posting']);
  });

  it('falls back to the posting view when the saved PDF goes away', async () => {
    const { fixture, el } = await render(fullPosting, '/api/applications/a1/documents/d1/content');
    clickTab(el, 'Saved PDF');
    await fixture.whenStable();

    fixture.componentRef.setInput('postingPdfUrl', null);
    await fixture.whenStable();

    expect(el.querySelector('iframe')).toBeNull();
    expect(el.querySelector('app-posting-view')).not.toBeNull();
  });
});

describe('PrintablePosting', () => {
  let http: HttpTestingController;

  async function render() {
    TestBed.configureTestingModule({
      imports: [PrintablePosting],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PrintablePosting);
    fixture.componentRef.setInput('id', 'a1');
    await fixture.whenStable();
    http.expectOne('/api/applications/a1').flush(fullPosting);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function button(el: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  it('shows the posting with print controls kept off the printout', async () => {
    const { el } = await render();

    expect(el.querySelector('app-posting-view h1')?.textContent).toBe('Senior Java Engineer');
    expect(el.querySelector('.toolbar')?.classList).toContain('no-print');
  });

  it('prints with the browser', async () => {
    const print = vi.spyOn(window, 'print').mockImplementation(() => {});
    const { el } = await render();

    button(el, 'Print').click();

    expect(print).toHaveBeenCalled();
  });

  it('saves the server-rendered PDF to the documents and links to it', async () => {
    const { fixture, el } = await render();

    button(el, 'Save as PDF to documents').click();
    const req = http.expectOne('/api/applications/a1/documents/posting-summary');
    expect(req.request.method).toBe('POST');
    req.flush(aDocument({ id: 'd9', kind: 'POSTING', filename: 'posting-acme-summary.pdf' }));
    await fixture.whenStable();

    const link = el.querySelector<HTMLAnchorElement>('.notice a')!;
    expect(link.textContent).toBe('posting-acme-summary.pdf');
    expect(link.getAttribute('href')).toBe('/api/applications/a1/documents/d9/content');
  });
});
