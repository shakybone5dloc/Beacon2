import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Board } from './board';
import { anApplication } from '../applications/testing';
import { provideRouter } from '@angular/router';

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
    expect(columns.length).toBe(5);
    expect(columns[0].textContent).toContain('Acme');
    expect(columns[1].textContent).toContain('Globex');
    expect(columns[0].textContent).not.toContain('Globex');
  });

  it('groups every closed status into the Closed column with a status badge', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    http
      .expectOne('/api/applications')
      .flush([
        anApplication({ company: 'Acme', status: 'ACCEPTED' }),
        anApplication({ company: 'Globex', status: 'REJECTED' }),
        anApplication({ company: 'Initech', status: 'OFFER' }),
      ]);
    await fixture.whenStable();

    const columns: HTMLElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('section.column'),
    );
    const closed = columns[4];
    expect(closed.querySelector('h2')?.textContent).toContain('Closed');
    expect(closed.querySelectorAll('.card').length).toBe(2);
    expect(closed.textContent).toContain('Accepted');
    expect(closed.textContent).toContain('Rejected');
    expect(columns[3].textContent).toContain('Initech');
    expect(columns[3].querySelector('.badge')).toBeNull();
  });

  it('links each card to its detail page', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    http.expectOne('/api/applications').flush([anApplication({ id: 'a1', company: 'Acme' })]);
    await fixture.whenStable();

    const link = fixture.nativeElement.querySelector('.card a.company');
    expect(link.textContent).toContain('Acme');
    expect(link.getAttribute('href')).toBe('/applications/a1');
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
