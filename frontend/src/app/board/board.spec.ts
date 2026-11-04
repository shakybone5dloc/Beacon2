import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Board } from './board';
import { anApplication } from '../applications/testing';
import { provideRouter, Router } from '@angular/router';
import { By } from '@angular/platform-browser';
import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';

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

  /** A menu item's words, without the arrow icon a forward move carries. */
  function itemLabel(item: HTMLElement): string {
    return (item.querySelector('.label') ?? item).textContent?.trim() ?? '';
  }

  /** Opens a card's menu and finds one of its items; the menu opens in an overlay outside the board. */
  async function menuItem(
    fixture: { nativeElement: HTMLElement; whenStable(): Promise<unknown> },
    company: string,
    label: string,
  ): Promise<HTMLElement> {
    const card = Array.from(
      fixture.nativeElement.querySelectorAll<HTMLElement>('.board .card'),
    ).find((c) => c.querySelector('a.company')?.textContent?.trim() === company)!;
    card.querySelector<HTMLButtonElement>('button.card-menu')!.click();
    await fixture.whenStable();
    return Array.from(document.querySelectorAll<HTMLElement>('.menu .menu-item')).find(
      (item) => itemLabel(item) === label,
    )!;
  }

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

  it('shows a fit bar on cards whose fit has been checked', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();

    http
      .expectOne('/api/applications')
      .flush([
        anApplication({ id: 'a1', company: 'Acme', fitScore: 72 }),
        anApplication({ id: 'a2', company: 'Initech', fitScore: null }),
      ]);
    await fixture.whenStable();

    const [checked, unchecked] = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('.card'),
    );
    const fit = checked.querySelector('.fit')!;
    expect(fit.querySelector('.fit-score')?.textContent).toBe('72');
    expect(fit.getAttribute('title')).toBe('Fit 72% · Good fit');
    const fill = fit.querySelector<HTMLElement>('.fit-fill')!;
    expect(fill.style.width).toBe('72%');
    expect(fill.classList).toContain('bg-info');
    expect(unchecked.querySelector('.fit')).toBeNull();
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

  it('moves a card from its menu', async () => {
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
    (await menuItem(fixture, 'Acme', 'Applied')).click();

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
    (await menuItem(fixture, 'Acme', 'Applied')).click();

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

  it('keeps the moves and other actions in a menu instead of buttons on the card', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();
    http.expectOne('/api/applications').flush([
      anApplication({
        id: 'a1',
        company: 'Acme',
        status: 'APPLIED',
        followUpOn: '2099-01-01',
        version: 4,
        allowedTransitions: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN'],
      }),
    ]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('.board .card button:not(.card-menu)').length).toBe(0);

    await menuItem(fixture, 'Acme', 'Open');
    const items = Array.from(document.querySelectorAll<HTMLElement>('.menu .menu-item'));
    expect(items.map(itemLabel)).toEqual([
      'Rejected',
      'Withdrawn',
      'Interviewing',
      'Open',
      'Interview brief',
      'Edit',
      'Follow-up done',
    ]);
    expect(items[2].querySelector('.material-symbols-outlined')?.textContent).toContain(
      'arrow_forward',
    );
    expect(items[4].getAttribute('href')).toBe('/applications/a1/brief');

    items[6].click();
    const req = http.expectOne('/api/applications/a1/follow-up');
    expect(req.request.body).toEqual({ followUpOn: null, version: 4 });
    req.flush(anApplication({ id: 'a1', company: 'Acme', status: 'APPLIED', version: 5 }));
    await fixture.whenStable();
    expect(document.querySelector('.menu')).toBeNull();
  });

  it('keeps long tags inside the card, with the full text on hover', async () => {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();
    http.expectOne('/api/applications').flush([
      anApplication({
        status: 'WITHDRAWN',
        location: 'Anywhere in the U.S. (remote job)',
        remote: true,
        salaryMin: 122000,
        salaryMax: 158000,
        salaryCurrency: 'USD',
        salaryPeriod: 'year',
      }),
    ]);
    await fixture.whenStable();

    const tags = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.card .tag'));
    expect(tags.map((t) => t.textContent?.trim())).toEqual([
      'Anywhere in the U.S.',
      'Remote',
      '$122k–$158k/yr',
    ]);
    expect(tags[0].getAttribute('title')).toBe('Anywhere in the U.S. (remote job)');
    expect(tags[0].classList).toContain('max-w-full');
    expect(tags[0].querySelector('.truncate')).not.toBeNull();
  });

  describe('search and filters', () => {
    // Only Initech needs action (an overdue follow-up); the others just changed status.
    const fresh = new Date().toISOString();
    const apps = [
      anApplication({
        id: 'a1',
        company: 'Acme',
        role: 'Senior Java Engineer',
        source: 'LinkedIn',
        statusChangedAt: fresh,
      }),
      anApplication({
        id: 'a2',
        statusChangedAt: fresh,
        company: 'Globex',
        role: 'Kotlin Developer',
        location: 'Montréal, QC',
        source: 'Himalayas',
        remote: true,
      }),
      anApplication({
        id: 'a3',
        company: 'Initech',
        role: 'Java Developer',
        notes: 'Referred by Sam',
        status: 'APPLIED',
        followUpOn: new Date(Date.now() - 2 * 86_400_000).toISOString().slice(0, 10),
      }),
    ];

    async function open(queryParams: Record<string, string> = {}) {
      const router = TestBed.inject(Router);
      await router.navigate([], { queryParams });
      const fixture = TestBed.createComponent(Board);
      await fixture.whenStable();
      http.expectOne('/api/applications').flush(apps);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement, router };
    }

    function companies(el: HTMLElement): string[] {
      return Array.from(el.querySelectorAll('.board .card a.company')).map(
        (a) => a.textContent?.trim() ?? '',
      );
    }

    it('finds cards by every word typed, in company, role, location or notes', async () => {
      const { fixture, el } = await open();
      const box = el.querySelector<HTMLInputElement>('input.search')!;

      box.value = 'java sam';
      box.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      expect(companies(el)).toEqual(['Initech']);

      box.value = 'montreal';
      box.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      expect(companies(el)).toEqual(['Globex']);
      expect(el.querySelector('.shown')?.textContent).toContain('Showing 1 of 3');
    });

    it('filters by source, remote and needs action, and clears them all', async () => {
      const { fixture, el } = await open();

      const source = el.querySelector<HTMLSelectElement>('select.source')!;
      expect(Array.from(source.options).map((o) => o.value)).toEqual(['', 'Himalayas', 'LinkedIn']);
      source.value = 'LinkedIn';
      source.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(companies(el)).toEqual(['Acme']);

      el.querySelector<HTMLButtonElement>('button.clear')!.click();
      await fixture.whenStable();
      el.querySelector<HTMLInputElement>('input.remote')!.click();
      await fixture.whenStable();
      expect(companies(el)).toEqual(['Globex']);

      el.querySelector<HTMLButtonElement>('button.clear')!.click();
      await fixture.whenStable();
      el.querySelector<HTMLInputElement>('input.needs-action')!.click();
      await fixture.whenStable();
      expect(companies(el)).toEqual(['Initech']);

      el.querySelector<HTMLButtonElement>('button.clear')!.click();
      await fixture.whenStable();
      expect(companies(el)).toHaveLength(3);
      expect(el.querySelector('.shown')).toBeNull();
    });

    it('keeps the filters in the address', async () => {
      const { fixture, el, router } = await open({ q: 'kotlin', remote: 'true' });

      expect(el.querySelector<HTMLInputElement>('input.search')!.value).toBe('kotlin');
      expect(el.querySelector<HTMLInputElement>('input.remote')!.checked).toBe(true);
      expect(companies(el)).toEqual(['Globex']);

      el.querySelector<HTMLInputElement>('input.needs-action')!.click();
      await fixture.whenStable();
      expect(router.url).toBe('/?q=kotlin&remote=true&action=true');
    });
  });

  describe('drag and drop', () => {
    async function open(apps: ReturnType<typeof anApplication>[]) {
      const fixture = TestBed.createComponent(Board);
      await fixture.whenStable();
      http.expectOne('/api/applications').flush(apps);
      await fixture.whenStable();
      const lists = fixture.debugElement
        .queryAll(By.directive(CdkDropList))
        .map((d) => d.injector.get(CdkDropList));
      return { fixture, el: fixture.nativeElement as HTMLElement, lists };
    }

    /** What the CDK reports when a card from one column is let go over another. */
    function drop(lists: CdkDropList[], from: number, to: number, app: unknown) {
      lists[to].dropped.emit({
        previousContainer: lists[from],
        container: lists[to],
        item: { data: app },
      } as unknown as CdkDragDrop<unknown>);
    }

    it('only lets a card into columns its allowed moves reach', async () => {
      const acme = anApplication({ status: 'SAVED', allowedTransitions: ['APPLIED', 'WITHDRAWN'] });
      const { lists } = await open([acme]);
      const drag = { data: acme } as CdkDrag<typeof acme>;

      const allowed = lists.map((list) => list.enterPredicate(drag, list));

      // Saved, Applied, Interviewing, Offer, Closed
      expect(allowed).toEqual([false, true, false, false, true]);
    });

    it('moves a card dropped on a column it can reach one way', async () => {
      const acme = anApplication({
        id: 'a1',
        company: 'Acme',
        status: 'SAVED',
        version: 3,
        allowedTransitions: ['APPLIED', 'WITHDRAWN'],
      });
      const { fixture, el, lists } = await open([acme]);

      drop(lists, 0, 1, acme);
      const req = http.expectOne('/api/applications/a1/status');
      expect(req.request.body).toEqual({ status: 'APPLIED', version: 3 });
      req.flush({ ...acme, status: 'APPLIED', version: 4, allowedTransitions: [] });
      await fixture.whenStable();

      expect(el.querySelectorAll('section.column')[1].textContent).toContain('Acme');
    });

    it('asks how to close a card dropped on Closed when there is more than one way', async () => {
      const acme = anApplication({
        id: 'a1',
        company: 'Acme',
        status: 'APPLIED',
        allowedTransitions: ['INTERVIEWING', 'REJECTED', 'WITHDRAWN'],
      });
      const { fixture, el, lists } = await open([acme]);

      drop(lists, 1, 4, acme);
      await fixture.whenStable();
      http.expectNone('/api/applications/a1/status');
      const choice = el.querySelector('.close-choice')!;
      expect(choice.textContent).toContain('Move Acme to Closed as:');
      const buttons = Array.from(choice.querySelectorAll('button')).map((b) =>
        b.textContent?.trim(),
      );
      expect(buttons).toEqual(['Rejected', 'Withdrawn', 'Cancel']);

      Array.from(choice.querySelectorAll('button'))
        .find((b) => b.textContent?.trim() === 'Rejected')!
        .click();
      const req = http.expectOne('/api/applications/a1/status');
      expect(req.request.body.status).toBe('REJECTED');
      req.flush({ ...acme, status: 'REJECTED', allowedTransitions: [] });
      await fixture.whenStable();
      expect(el.querySelector('.close-choice')).toBeNull();
    });

    it('does nothing when a card is let go over its own column, or the choice is cancelled', async () => {
      const acme = anApplication({
        id: 'a1',
        status: 'APPLIED',
        allowedTransitions: ['REJECTED', 'WITHDRAWN'],
      });
      const { fixture, el, lists } = await open([acme]);

      drop(lists, 1, 1, acme);
      drop(lists, 1, 4, acme);
      await fixture.whenStable();
      el.querySelector<HTMLButtonElement>('.close-choice button.cancel')!.click();
      await fixture.whenStable();

      http.expectNone('/api/applications/a1/status');
      expect(el.querySelector('.close-choice')).toBeNull();
    });
  });
});
