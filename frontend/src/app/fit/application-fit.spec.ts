import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ApplicationFit } from './application-fit';
import { Fit } from './fit.model';
import { aFit } from './testing';
import { aResume } from '../resumes/testing';
import { aDocument } from '../documents/testing';

describe('ApplicationFit', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function render(fit: Fit | null, configured = true, hasJobDescription = true) {
    const fixture = TestBed.createComponent(ApplicationFit);
    fixture.componentRef.setInput('applicationId', 'app-1');
    fixture.componentRef.setInput('hasJobDescription', hasJobDescription);
    fixture.detectChanges();
    http.expectOne('/api/applications/app-1/fit').flush({ fit });
    http.expectOne('/api/fit/status').flush({ configured });
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function button(el: HTMLElement, label: string): HTMLButtonElement | undefined {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label);
  }

  async function openPicker(fixture: Awaited<ReturnType<typeof render>>['fixture'], label: string) {
    const el: HTMLElement = fixture.nativeElement;
    button(el, label)!.click();
    http
      .expectOne('/api/applications/app-1/documents')
      .flush([aDocument({ id: 'own', kind: 'RESUME', filename: 'tailored.pdf' })]);
    http
      .expectOne('/api/resumes')
      .flush([
        aResume({ documentId: 'r1', isDefault: false, label: 'QA' }),
        aResume({ documentId: 'r2', label: 'Backend' }),
      ]);
    await fixture.whenStable();
  }

  it('shows the latest fit with its matches, gaps and dealbreakers', async () => {
    const { el } = await render(aFit({ score: 64, dealbreakers: ['Secret clearance'] }));

    expect(el.querySelector('.score')?.textContent).toBe('64');
    expect(el.querySelector('.verdict')?.textContent).toBe('Stretch');
    expect(el.querySelector('.dealbreakers')?.textContent).toContain('Secret clearance');
    expect(Array.from(el.querySelectorAll('.matched li')).map((li) => li.textContent)).toEqual([
      'Java',
      'Spring Boot',
    ]);
    expect(el.querySelector('.gaps li')?.textContent).toBe('Kubernetes');
    expect(el.querySelector('.stale')).toBeNull();
    expect(button(el, 'Check again')).toBeTruthy();
  });

  it('says when the fit is out of date', async () => {
    const { el } = await render(aFit({ current: false }));

    expect(el.querySelector('.stale')?.textContent).toContain('has changed since this check');
  });

  it('checks fit with the default resume picked, then shows the result', async () => {
    const { fixture, el } = await render(null);
    const scored: Fit[] = [];
    fixture.componentInstance.scored.subscribe((f) => scored.push(f));

    await openPicker(fixture, 'Check fit with AI');
    const options = Array.from(el.querySelectorAll('option')).map((o) => o.textContent);
    expect(options).toEqual(['tailored.pdf (this application)', 'QA', 'Backend (default)']);
    expect(el.querySelector('select')!.value).toBe('r2');

    button(el, 'Check')!.click();
    const req = http.expectOne(
      (r) => r.method === 'POST' && r.url === '/api/applications/app-1/fit',
    );
    expect(req.request.body).toEqual({ resumeDocumentId: 'r2' });
    req.flush(aFit({ score: 91 }));
    await fixture.whenStable();

    expect(el.querySelector('.score')?.textContent).toBe('91');
    expect(el.querySelector('select')).toBeNull();
    expect(scored.map((f) => f.score)).toEqual([91]);
  });

  it('checks again with the resume used last time', async () => {
    const { fixture, el } = await render(aFit({ resumeDocumentId: 'r1' }));

    await openPicker(fixture, 'Check again');

    expect(el.querySelector('select')!.value).toBe('r1');
  });

  it('shows why a check failed', async () => {
    const { fixture, el } = await render(null);
    await openPicker(fixture, 'Check fit with AI');

    button(el, 'Check')!.click();
    http
      .expectOne('/api/applications/app-1/fit')
      .flush(
        { detail: 'OpenAI rejected your API key.' },
        { status: 422, statusText: 'Unprocessable' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('rejected your API key');
  });

  it('cannot check without a job description', async () => {
    const { fixture, el } = await render(null, true, false);
    await openPicker(fixture, 'Check fit with AI');

    expect(el.textContent).toContain('no job description yet');
    expect(button(el, 'Check')!.disabled).toBe(true);
  });

  it('points to Settings without an AI key', async () => {
    const { el } = await render(null, false);

    expect(el.querySelector('a[href="/settings/api-keys"]')).not.toBeNull();
    expect(button(el, 'Check fit with AI')).toBeUndefined();
  });
});
