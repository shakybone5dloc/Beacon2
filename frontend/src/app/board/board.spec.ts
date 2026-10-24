import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Board } from './board';
import { ApplicationResponse } from '../applications/application.model';
import { provideRouter } from '@angular/router';

function anApplication(overrides: Partial<ApplicationResponse> = {}): ApplicationResponse {
  return {
    id: crypto.randomUUID(),
    company: 'Acme',
    role: 'Engineer',
    jobUrl: null,
    notes: null,
    status: 'SAVED',
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    version: 0,
    allowedTransitions: [],
    ...overrides,
  };
}

describe('Board', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Board],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('puts each application in its status column', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    http
      .expectOne('/api/applications')
      .flush([
        anApplication({ company: 'Acme', status: 'SAVED' }),
        anApplication({ company: 'Globex', status: 'APPLIED' }),
      ]);
    await fixture.whenStable();

    const columns: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('section.column'),
    );
    expect(columns.length).toBe(6);
    expect(columns[0].textContent).toContain('Acme');
    expect(columns[1].textContent).toContain('Globex');
    expect(columns[0].textContent).not.toContain('Globex');
  });

  it('shows Loading... until the applications arrive', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Loading...');

    http.expectOne('/api/applications').flush([]);
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).not.toContain('Loading...');
  });

  it('shows a notice when the applications cannot be loaded', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    http.expectOne('/api/applications').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('Could not load applications');
    expect(fixture.nativeElement.querySelector('.board')).toBeNull();
  });

  it('moves a card when a transition button is clicked', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    const acme = anApplication({
      id: 'a1',
      company: 'Acme',
      status: 'SAVED',
      allowedTransitions: ['APPLIED', 'WITHDRAWN'],
    });
    http.expectOne('/api/applications').flush([acme]);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const applied = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Applied',
    )!;
    applied.click();

    const req = http.expectOne('/api/applications/a1/status');
    expect(req.request.body).toEqual({ status: 'APPLIED', version: 0 });
    req.flush({
      ...acme,
      status: 'APPLIED',
      version: 1,
      allowedTransitions: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN'],
    });
    await fixture.whenStable();

    const columns = el.querySelectorAll('section.column');
    expect(columns[0].textContent).not.toContain('Acme');
    expect(columns[1].textContent).toContain('Acme');
  });

  it('shows a notice and reloads the board when the move conflicts', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    const acme = anApplication({
      id: 'a1',
      company: 'Acme',
      status: 'SAVED',
      allowedTransitions: ['APPLIED', 'WITHDRAWN'],
    });
    http.expectOne('/api/applications').flush([acme]);
    await fixture.whenStable();

    const el: HTMLElement = fixture.nativeElement;
    const applied = Array.from(el.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Applied',
    )!;
    applied.click();

    http
      .expectOne('/api/applications/a1/status')
      .flush({ title: 'Conflict' }, { status: 409, statusText: 'Conflict' });

    // Someone else already moved it; the reload brings back the current state.
    http
      .expectOne('/api/applications')
      .flush([{ ...acme, status: 'APPLIED', version: 1, allowedTransitions: [] }]);
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('changed somewhere else');
    const columns = el.querySelectorAll('section.column');
    expect(columns[0].textContent).not.toContain('Acme');
    expect(columns[1].textContent).toContain('Acme');
  });
});
