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

  describe('attention', () => {
    const DAY = 24 * 60 * 60 * 1000;
    const daysAgo = (days: number) => new Date(Date.now() - days * DAY - 60_000).toISOString();
    /** Local calendar date `days` from today, as YYYY-MM-DD. */
    const dateIn = (days: number) => {
      const d = new Date(Date.now() + days * DAY);
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    };

    async function render(...apps: ReturnType<typeof anApplication>[]) {
      const fixture = TestBed.createComponent(Board);
      await fixture.whenStable();
      http.expectOne('/api/applications').flush(apps);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    function cardFor(el: HTMLElement, company: string): HTMLElement {
      return Array.from(el.querySelectorAll<HTMLElement>('.card')).find((c) =>
        c.textContent?.includes(company),
      )!;
    }

    it('shows each active card how long it has been in its status', async () => {
      const { el } = await render(
        anApplication({ company: 'Acme', status: 'APPLIED', statusChangedAt: daysAgo(3) }),
      );

      const age = cardFor(el, 'Acme').querySelector('.age')!;
      expect(age.textContent?.trim()).toBe('3d');
      expect(age.getAttribute('title')).toBe('In Applied for 3 days.');
      expect(cardFor(el, 'Acme').classList).not.toContain('flagged');
    });

    it('flags cards and lists them in the attention strip, dated reasons first', async () => {
      const { el } = await render(
        anApplication({ company: 'Acme', status: 'APPLIED', statusChangedAt: daysAgo(8) }),
        anApplication({ company: 'Globex', status: 'INTERVIEWING', followUpOn: dateIn(-2) }),
        anApplication({ company: 'Initech', status: 'SAVED', applyBy: dateIn(1) }),
        anApplication({ company: 'Hooli', status: 'INTERVIEWING', statusChangedAt: daysAgo(30) }),
      );

      expect(cardFor(el, 'Acme').classList).toContain('flagged');
      expect(cardFor(el, 'Globex').classList).toContain('flagged');
      expect(cardFor(el, 'Initech').classList).toContain('flagged');
      expect(cardFor(el, 'Hooli').classList).not.toContain('flagged');

      const items = Array.from(el.querySelectorAll('.attention li')).map((li) =>
        li.textContent!.replace(/\s+/g, ' ').trim(),
      );
      expect(items.length).toBe(3);
      expect(items[0]).toContain('Globex');
      expect(items[0]).toContain('Follow-up 2d overdue');
      expect(items[1]).toContain('Initech');
      expect(items[1]).toContain('Closes in 1d');
      expect(items[2]).toContain('Acme');
      expect(items[2]).toContain('No movement in 8 days');
    });

    it('shows date chips on cards', async () => {
      const { el } = await render(
        anApplication({
          company: 'Acme',
          status: 'SAVED',
          applyBy: dateIn(10),
          followUpOn: dateIn(4),
        }),
      );

      const card = cardFor(el, 'Acme');
      expect(card.querySelector('.chip.deadline')?.textContent).toContain('Closes in 10d');
      expect(card.querySelector('.chip.follow-up')?.textContent).toContain('Follow up in 4d');
      expect(card.querySelector('.chip.urgent')).toBeNull();
      expect(el.querySelector('.attention')).toBeNull();
    });

    it('shows the next interview on its card', async () => {
      const tomorrowAt2 = new Date(Date.now() + DAY);
      tomorrowAt2.setHours(14, 0, 0, 0);
      const { el } = await render(
        anApplication({
          company: 'Acme',
          status: 'INTERVIEWING',
          nextInterviewAt: tomorrowAt2.toISOString(),
        }),
      );

      expect(cardFor(el, 'Acme').querySelector('.chip.interview')?.textContent).toContain(
        'Interview tomorrow 2:00 PM',
      );
    });

    it('clears a due follow-up from the strip with Done', async () => {
      const app = anApplication({
        id: 'a1',
        company: 'Globex',
        status: 'INTERVIEWING',
        version: 2,
        followUpOn: dateIn(0),
      });
      const { fixture, el } = await render(app);

      el.querySelector<HTMLButtonElement>('.attention button')!.click();

      const req = http.expectOne('/api/applications/a1/follow-up');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ followUpOn: null, version: 2 });
      req.flush({ ...app, followUpOn: null, version: 3 });
      await fixture.whenStable();

      expect(el.querySelector('.attention')).toBeNull();
      expect(cardFor(el, 'Globex').querySelector('.chip')).toBeNull();
    });

    it('leaves closed cards without an age and out of the strip', async () => {
      const { el } = await render(
        anApplication({
          company: 'Acme',
          status: 'REJECTED',
          statusChangedAt: daysAgo(90),
          followUpOn: dateIn(-5),
        }),
      );

      expect(cardFor(el, 'Acme').querySelector('.age')).toBeNull();
      expect(cardFor(el, 'Acme').classList).not.toContain('flagged');
      expect(el.querySelector('.attention')).toBeNull();
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
