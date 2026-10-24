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

  describe('time in status', () => {
    const daysAgo = (days: number) =>
      new Date(Date.now() - days * 24 * 60 * 60 * 1000 - 60_000).toISOString();

    async function render(...apps: ReturnType<typeof anApplication>[]) {
      const fixture = TestBed.createComponent(Board);
      await fixture.whenStable();
      http.expectOne('/api/applications').flush(apps);
      await fixture.whenStable();
      return fixture.nativeElement as HTMLElement;
    }

    function cardFor(el: HTMLElement, company: string): HTMLElement {
      return Array.from(el.querySelectorAll<HTMLElement>('.card')).find((c) =>
        c.textContent?.includes(company),
      )!;
    }

    it('shows each active card how long it has been in its status', async () => {
      const el = await render(
        anApplication({ company: 'Acme', status: 'APPLIED', statusChangedAt: daysAgo(3) }),
      );

      const age = cardFor(el, 'Acme').querySelector('.age')!;
      expect(age.textContent?.trim()).toBe('3d');
      expect(age.getAttribute('title')).toBe('In Applied for 3 days.');
      expect(cardFor(el, 'Acme').classList).not.toContain('stale');
    });

    it('highlights cards past their status limit and counts them', async () => {
      const el = await render(
        anApplication({ company: 'Acme', status: 'APPLIED', statusChangedAt: daysAgo(8) }),
        anApplication({ company: 'Globex', status: 'OFFER', statusChangedAt: daysAgo(4) }),
        anApplication({ company: 'Initech', status: 'INTERVIEWING', statusChangedAt: daysAgo(30) }),
      );

      expect(cardFor(el, 'Acme').classList).toContain('stale');
      expect(cardFor(el, 'Globex').classList).toContain('stale');
      expect(cardFor(el, 'Initech').classList).not.toContain('stale');
      expect(el.querySelector('.quiet')?.textContent).toContain('2 applications have gone quiet');
    });

    it('leaves closed cards without an age and out of the count', async () => {
      const el = await render(
        anApplication({ company: 'Acme', status: 'REJECTED', statusChangedAt: daysAgo(90) }),
      );

      expect(cardFor(el, 'Acme').querySelector('.age')).toBeNull();
      expect(cardFor(el, 'Acme').classList).not.toContain('stale');
      expect(el.querySelector('.quiet')).toBeNull();
    });
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
