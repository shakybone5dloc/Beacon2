import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { aResume } from '../resumes/testing';
import { aDocument } from '../documents/testing';
import { ApplicationResume } from './application-resume';
import { ResumeSuggestion, splitSuggestions, Tailoring } from './tailoring.model';

function aSuggestion(overrides: Partial<ResumeSuggestion> = {}): ResumeSuggestion {
  return {
    id: 's1',
    kind: 'REWORD',
    section: 'Experience: Acme',
    suggestion: 'Say "Spring Boot" rather than "Spring".',
    reason: 'The posting asks for Spring Boot by name.',
    example: 'Built REST APIs in Spring Boot 3.',
    state: 'OPEN',
    createdAt: '2026-11-07T12:00:00Z',
    ...overrides,
  };
}

function aTailoring(overrides: Partial<Tailoring> = {}): Tailoring {
  return {
    sentResumeDocumentId: null,
    resumeDocumentId: null,
    resumeLabel: null,
    suggestions: [],
    configured: true,
    ...overrides,
  };
}

it('puts open suggestions first and keeps dismissed ones apart', () => {
  const { active, dismissed } = splitSuggestions([
    aSuggestion({ id: 'a', state: 'DONE' }),
    aSuggestion({ id: 'b', state: 'DISMISSED' }),
    aSuggestion({ id: 'c' }),
  ]);
  expect(active.map((s) => s.id)).toEqual(['c', 'a']);
  expect(dismissed.map((s) => s.id)).toEqual(['b']);
});

describe('ApplicationResume', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(tailoring: Tailoring, hasJobDescription = true) {
    const fixture = TestBed.createComponent(ApplicationResume);
    fixture.componentRef.setInput('applicationId', 'app-1');
    fixture.componentRef.setInput('hasJobDescription', hasJobDescription);
    fixture.detectChanges();
    http.expectOne('/api/applications/app-1/resume').flush(tailoring);
    http
      .expectOne('/api/applications/app-1/documents')
      .flush([aDocument({ id: 'own', kind: 'RESUME', filename: 'ghx.pdf' })]);
    http
      .expectOne('/api/resumes')
      .flush([
        aResume({ documentId: 'r1', isDefault: false, label: 'QA' }),
        aResume({ documentId: 'r2', label: 'Backend' }),
      ]);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function button(el: HTMLElement, label: string): HTMLButtonElement | undefined {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  it('records the resume sent, offering this application and library resumes', async () => {
    const { fixture, el } = await render(aTailoring());

    const sent = el.querySelector<HTMLSelectElement>('.sent select')!;
    expect(Array.from(sent.options).map((o) => o.textContent)).toEqual([
      'Not recorded',
      'ghx.pdf (this application)',
      'QA',
      'Backend (default)',
    ]);
    sent.value = 'own';
    sent.dispatchEvent(new Event('change'));

    const req = http.expectOne('/api/applications/app-1/resume/sent');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ resumeDocumentId: 'own' });
    req.flush(aTailoring({ sentResumeDocumentId: 'own' }));
    await fixture.whenStable();
    expect(sent.value).toBe('own');
  });

  it('asks for suggestions with the default resume, then works through them', async () => {
    const { fixture, el } = await render(aTailoring());

    button(el, 'Suggest changes with AI')!.click();
    const req = http.expectOne('/api/applications/app-1/resume/suggestions');
    expect(req.request.body).toEqual({ resumeDocumentId: 'r2' });
    req.flush(
      aTailoring({
        resumeDocumentId: 'r2',
        resumeLabel: 'Backend',
        suggestions: [
          aSuggestion(),
          aSuggestion({ id: 's2', kind: 'MOVE', section: 'Skills', example: null }),
        ],
      }),
    );
    await fixture.whenStable();

    const items = el.querySelectorAll('.suggestion');
    expect(items.length).toBe(2);
    expect(items[0].querySelector('.kind')?.textContent).toBe('Reword');
    expect(items[0].querySelector('.example')?.textContent).toContain('Spring Boot 3');
    expect(items[1].querySelector('.example')).toBeNull();
    expect(el.textContent).toContain('2 to go');

    (items[0].querySelector('.mark-done') as HTMLButtonElement).click();
    const done = http.expectOne('/api/applications/app-1/resume/suggestions/s1');
    expect(done.request.body).toEqual({ state: 'DONE' });
    done.flush(aSuggestion({ state: 'DONE' }));
    await fixture.whenStable();

    // Open ones stay on top; the done one moves below and can be reopened.
    const after = el.querySelectorAll('.suggestion');
    expect(after[1].classList).toContain('done');
    expect(after[1].querySelector('.reopen')).toBeTruthy();
    expect(el.textContent).toContain('1 to go');
    expect(button(el, 'Suggest again')).toBeTruthy();
  });

  it('keeps dismissed suggestions out of the way until asked for', async () => {
    const { fixture, el } = await render(
      aTailoring({
        resumeDocumentId: 'r1',
        resumeLabel: 'QA',
        suggestions: [aSuggestion({ state: 'DISMISSED' })],
      }),
    );

    expect(el.querySelectorAll('.suggestion').length).toBe(0);
    expect(el.querySelector<HTMLSelectElement>('label:not(.sent) select')!.value).toBe('r1');
    button(el, 'Show 1 dismissed')!.click();
    await fixture.whenStable();
    expect(el.querySelector('.dismissed')?.textContent).toContain('Spring Boot');
  });

  it('says what is missing before suggestions can be made', async () => {
    const noKey = await render(aTailoring({ configured: false }));
    expect(noKey.el.textContent).toContain('Settings · API keys');
    expect(button(noKey.el, 'Suggest changes with AI')).toBeUndefined();
    noKey.fixture.destroy();

    const noDescription = await render(aTailoring(), false);
    expect(noDescription.el.querySelector('.notice')?.textContent).toContain('no job description');
  });
});
