import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CoverLetterDrafter } from './cover-letter-drafter';
import { aDocument } from './testing';
import { DocumentResponse } from './document.model';

describe('CoverLetterDrafter', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CoverLetterDrafter],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(configured = true, hasJobDescription = true) {
    const fixture = TestBed.createComponent(CoverLetterDrafter);
    fixture.componentRef.setInput('applicationId', 'a1');
    fixture.componentRef.setInput('hasJobDescription', hasJobDescription);
    await fixture.whenStable();
    http.expectOne('/api/cover-letters/status').flush({ configured });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  async function open(
    fixture: Awaited<ReturnType<typeof render>>['fixture'],
    own: DocumentResponse[],
    library: DocumentResponse[],
  ) {
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>('button.start')!
      .click();
    http.expectOne('/api/applications/a1/documents').flush(own);
    http.expectOne('/api/documents').flush(library);
    await fixture.whenStable();
  }

  function button(el: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  it('points to the API key settings when the user has no AI key', async () => {
    const { el } = await render(false);

    expect(el.textContent).toContain('add an OpenAI or Anthropic key');
    expect(el.querySelector('a[href="/settings/api-keys"]')).not.toBeNull();
    expect(el.querySelector('button.start')).toBeNull();
  });

  it('offers resumes from the application first, then the library', async () => {
    const { fixture, el } = await render();

    await open(
      fixture,
      [
        aDocument({ id: 'own', filename: 'tailored.pdf' }),
        aDocument({ id: 'post', kind: 'POSTING' }),
      ],
      [aDocument({ id: 'lib', applicationId: null, filename: 'main.pdf' })],
    );

    const options = Array.from(el.querySelectorAll('option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['tailored.pdf (this application)', 'main.pdf (library)']);
  });

  it('drafts, lets the draft be edited, and saves it', async () => {
    const { fixture, el } = await render();
    let saved: DocumentResponse | undefined;
    fixture.componentInstance.saved.subscribe((d) => (saved = d));
    await open(fixture, [], [aDocument({ id: 'lib', applicationId: null })]);

    const guidance = el.querySelector<HTMLInputElement>('input')!;
    guidance.value = 'Mention Denver';
    guidance.dispatchEvent(new Event('input'));
    button(el, 'Draft').click();

    const draftReq = http.expectOne('/api/applications/a1/cover-letter/draft');
    expect(draftReq.request.body).toEqual({
      resumeDocumentId: 'lib',
      instructions: 'Mention Denver',
    });
    draftReq.flush({ text: 'Dear Hiring Team,\n\nHello.' });
    await fixture.whenStable();

    const textarea = el.querySelector('textarea')!;
    expect(textarea.value).toBe('Dear Hiring Team,\n\nHello.');
    textarea.value = 'Dear Hiring Team,\n\nEdited.';
    textarea.dispatchEvent(new Event('input'));

    button(el, 'Save as PDF').click();
    const saveReq = http.expectOne('/api/applications/a1/cover-letter');
    expect(saveReq.request.body).toEqual({ text: 'Dear Hiring Team,\n\nEdited.' });
    saveReq.flush(aDocument({ kind: 'COVER_LETTER' }));
    await fixture.whenStable();

    expect(saved?.kind).toBe('COVER_LETTER');
    expect(el.querySelector('textarea')).toBeNull();
  });

  it('cannot draft until the application has a job description', async () => {
    const { fixture, el } = await render(true, false);
    await open(fixture, [], [aDocument({ applicationId: null })]);

    expect(el.textContent).toContain('no job description yet');
    expect(button(el, 'Draft').disabled).toBe(true);
  });

  it('asks for a resume when there are none', async () => {
    const { fixture, el } = await render();
    await open(fixture, [], []);

    expect(el.textContent).toContain('Upload a resume');
    expect(button(el, 'Draft')).toBeUndefined();
  });

  it('shows why a draft failed', async () => {
    const { fixture, el } = await render();
    await open(fixture, [], [aDocument({ applicationId: null })]);

    button(el, 'Draft').click();
    http
      .expectOne('/api/applications/a1/cover-letter/draft')
      .flush(
        { detail: 'Beacon can read PDF, text and Markdown resumes.' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('Beacon can read PDF');
  });
});
