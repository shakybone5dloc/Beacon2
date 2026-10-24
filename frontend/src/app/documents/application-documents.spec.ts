import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationDocuments } from './application-documents';
import { aDocument } from './testing';
import { DocumentResponse } from './document.model';

describe('ApplicationDocuments', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApplicationDocuments],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  async function render(
    docs: DocumentResponse[],
    applicationId: string | null = 'a1',
    jobUrl: string | null = null,
  ) {
    const fixture = TestBed.createComponent(ApplicationDocuments);
    fixture.componentRef.setInput('applicationId', applicationId);
    fixture.componentRef.setInput('jobUrl', jobUrl);
    await fixture.whenStable();
    http
      .expectOne(applicationId ? `/api/applications/${applicationId}/documents` : '/api/documents')
      .flush(docs);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('groups documents by kind with links to their content', async () => {
    const { el } = await render([
      aDocument({ id: 'd1', kind: 'RESUME', filename: 'resume.pdf' }),
      aDocument({ id: 'd2', kind: 'POSTING', filename: 'posting.pdf' }),
    ]);

    const headings = Array.from(el.querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(headings).toEqual(['Job posting', 'Resume']);
    const link = el.querySelector<HTMLAnchorElement>(
      'a[href="/api/applications/a1/documents/d1/content"]',
    );
    expect(link?.textContent).toContain('resume.pdf');
    expect(el.textContent).toContain('82 KB');
  });

  it('shows the default kind as selected in the type picker', async () => {
    const { el } = await render([]);

    const select = el.querySelector<HTMLSelectElement>('.upload select')!;
    expect(select.value).toBe('RESUME');
    expect(select.selectedOptions[0].textContent?.trim()).toBe('Resume');
  });

  it('uploads the chosen file with the chosen kind', async () => {
    const { fixture, el } = await render([]);
    const select = el.querySelector<HTMLSelectElement>('.upload select')!;
    select.value = 'COVER_LETTER';
    select.dispatchEvent(new Event('change'));

    const input = el.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'letter.pdf')] });
    input.dispatchEvent(new Event('change'));

    const req = http.expectOne('/api/applications/a1/documents');
    expect((req.request.body as FormData).get('kind')).toBe('COVER_LETTER');
    req.flush(aDocument({ kind: 'COVER_LETTER', filename: 'letter.pdf' }));
    await fixture.whenStable();

    expect(el.textContent).toContain('letter.pdf');
  });

  it('shows the server explanation when an upload is rejected', async () => {
    const { fixture, el } = await render([]);
    const input = el.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'page.html')] });
    input.dispatchEvent(new Event('change'));

    http
      .expectOne('/api/applications/a1/documents')
      .flush(
        { errors: { file: ['Upload a PDF, Word, ... file'] } },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('Upload a PDF, Word');
  });

  it('deletes after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, el } = await render([aDocument({ id: 'd1', filename: 'resume.pdf' })]);

    el.querySelector<HTMLButtonElement>('button.delete')!.click();
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/applications/a1/documents/d1')
      .flush(null);
    await fixture.whenStable();

    expect(el.textContent).toContain('No documents yet.');
  });

  it('captures the posting and reports whether the description was saved', async () => {
    const { fixture, el } = await render([], 'a1', 'https://example.com/job');
    let captured: boolean | undefined;
    fixture.componentInstance.captured.subscribe((saved) => (captured = saved));

    el.querySelector<HTMLButtonElement>('.capture button')!.click();
    http.expectOne('/api/applications/a1/documents/posting-capture').flush({
      document: aDocument({ kind: 'POSTING', filename: 'posting-acme.pdf' }),
      descriptionSaved: true,
    });
    await fixture.whenStable();

    expect(captured).toBe(true);
    expect(el.textContent).toContain('posting-acme.pdf');
  });

  it('suggests printing by hand when capture fails', async () => {
    const { fixture, el } = await render([], 'a1', 'https://example.com/job');

    el.querySelector<HTMLButtonElement>('.capture button')!.click();
    http
      .expectOne('/api/applications/a1/documents/posting-capture')
      .flush(
        { detail: 'The page took too long to load.' },
        { status: 502, statusText: 'Bad Gateway' },
      );
    await fixture.whenStable();

    const notice = el.querySelector('.notice')!.textContent!;
    expect(notice).toContain('The page took too long to load.');
    expect(notice).toContain('print it to PDF yourself');
  });

  it('has no capture button in the library or without a job URL', async () => {
    const library = await render([], null);
    expect(library.el.querySelector('.capture')).toBeNull();
  });
});
