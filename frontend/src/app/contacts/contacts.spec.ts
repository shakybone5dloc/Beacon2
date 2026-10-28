import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { anApplication } from '../applications/testing';
import { ContactLog } from './contact-log';
import { Contact, PossibleDuplicate } from './contact.model';
import { PossibleDuplicates } from './possible-duplicates';

function aContact(overrides: Partial<Contact> = {}): Contact {
  return {
    id: 'c1',
    personId: 'p1',
    personName: 'Pat Lee',
    applicationId: 'a1',
    company: 'Acme',
    jobRole: 'Java Engineer',
    channel: 'LINKEDIN',
    direction: 'INBOUND',
    occurredAt: '2026-10-20T15:00:00Z',
    body: 'Hi! I have a Java role at Acme.',
    version: 0,
    createdAt: '2026-10-20T15:00:00Z',
    updatedAt: '2026-10-20T15:00:00Z',
    ...overrides,
  };
}

function set(
  input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
  value: string,
): void {
  input.value = value;
  input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
}

/** Buttons with an icon also carry the icon's name in their text, so match the end. */
function button(el: HTMLElement, label: string): HTMLButtonElement {
  return Array.from(el.querySelectorAll('button')).find((b) =>
    b.textContent?.trim().endsWith(label),
  )!;
}

describe('contacts', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  describe('ContactLog on an application', () => {
    async function open(contacts: Contact[]) {
      const fixture = TestBed.createComponent(ContactLog);
      fixture.componentRef.setInput('applicationId', 'a1');
      const logged = vi.fn();
      fixture.componentInstance.logged.subscribe(logged);
      fixture.detectChanges();
      http.expectOne('/api/applications/a1/contacts').flush(contacts);
      await fixture.whenStable();
      return { fixture, logged, el: fixture.nativeElement as HTMLElement };
    }

    it('lists contacts with who they were with', async () => {
      const { el } = await open([aContact()]);

      expect(el.querySelector('.who')?.textContent).toContain('From Pat Lee');
      expect(el.querySelector('.who a')?.getAttribute('href')).toBe('/people/p1');
      expect(el.querySelector('.body')?.textContent).toContain('Java role at Acme');
      // On the application's own page, the "About" line would only repeat it.
      expect(el.querySelector('.about')).toBeNull();
    });

    it('logs a contact with someone picked from people, then reloads', async () => {
      const { fixture, logged, el } = await open([]);
      expect(el.querySelector('.empty')?.textContent).toContain('Nothing logged yet');

      button(el, 'Log contact').click();
      http.expectOne('/api/people').flush([
        {
          id: 'p1',
          name: 'Pat Lee',
          company: 'Robert Half',
        },
      ]);
      await fixture.whenStable();

      const form = el.querySelector<HTMLFormElement>('form')!;
      set(form.querySelector('select[formControlName=other]')!, 'p1');
      set(form.querySelector('input[formControlName=occurredAt]')!, '2026-10-20T11:00');
      set(form.querySelector('textarea')!, '  Sounds good, send it over.  ');
      form.querySelector<HTMLInputElement>('input[value=OUTBOUND]')!.click();
      form.dispatchEvent(new Event('submit'));

      const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/contacts');
      expect(req.request.body).toEqual({
        personId: 'p1',
        applicationId: 'a1',
        channel: 'LINKEDIN',
        direction: 'OUTBOUND',
        occurredAt: new Date('2026-10-20T11:00').toISOString(),
        body: 'Sounds good, send it over.',
      });
      req.flush(aContact({ direction: 'OUTBOUND' }));
      http.expectOne('/api/applications/a1/contacts').flush([aContact({ direction: 'OUTBOUND' })]);
      await fixture.whenStable();

      expect(logged).toHaveBeenCalled();
      expect(el.querySelector('form')).toBeNull();
      expect(el.querySelector('.who')?.textContent).toContain('To Pat Lee');
    });

    it('asks who it was with before saving', async () => {
      const { fixture, el } = await open([]);
      button(el, 'Log contact').click();
      http.expectOne('/api/people').flush([]);
      await fixture.whenStable();

      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(el.textContent).toContain('Pick who it was with.');
    });

    it('collapses a long message until asked', async () => {
      const { fixture, el } = await open([aContact({ body: 'word '.repeat(100) })]);

      expect(el.querySelector('.body')?.classList).toContain('line-clamp-4');
      button(el, 'Show all').click();
      await fixture.whenStable();
      expect(el.querySelector('.body')?.classList).not.toContain('line-clamp-4');
    });

    it('deletes after confirming', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const { fixture, el } = await open([aContact()]);

      button(el, 'Delete').click();
      http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/contacts/c1').flush(null);
      await fixture.whenStable();

      expect(el.querySelector('.contacts')).toBeNull();
    });
  });

  describe('ContactLog on a person', () => {
    async function open(contacts: Contact[]) {
      const fixture = TestBed.createComponent(ContactLog);
      fixture.componentRef.setInput('personId', 'p1');
      fixture.detectChanges();
      http.expectOne('/api/people/p1/contacts').flush(contacts);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('shows what each contact was about', async () => {
      const { el } = await open([aContact({ company: null })]);

      const about = el.querySelector('.about a');
      expect(about?.textContent).toContain('Undisclosed client – Java Engineer');
      expect(about?.getAttribute('href')).toBe('/applications/a1');
    });

    it('logs a call the user made, about no application', async () => {
      const { fixture, el } = await open([]);

      button(el, 'Log a call').click();
      http.expectOne('/api/applications').flush([anApplication({ role: 'Java Engineer' })]);
      await fixture.whenStable();
      const form = el.querySelector<HTMLFormElement>('form')!;
      expect(form.querySelector<HTMLSelectElement>('select[formControlName=channel]')!.value).toBe(
        'PHONE',
      );
      expect(
        Array.from(form.querySelectorAll('select[formControlName=other] option')).map((o) =>
          o.textContent?.trim(),
        ),
      ).toContain('Acme – Java Engineer');

      form.dispatchEvent(new Event('submit'));
      const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/contacts');
      expect(req.request.body).toMatchObject({
        personId: 'p1',
        applicationId: null,
        channel: 'PHONE',
        direction: 'OUTBOUND',
        body: null,
      });
      req.flush(aContact({ channel: 'PHONE' }));
      http.expectOne('/api/people/p1/contacts').flush([]);
    });

    it('edits with the version it was loaded at', async () => {
      const { fixture, el } = await open([aContact({ version: 3 })]);

      button(el, 'Edit').click();
      http.expectOne('/api/applications').flush([]);
      await fixture.whenStable();
      el.querySelector('form')!.dispatchEvent(new Event('submit'));

      const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/contacts/c1');
      expect(req.request.body).toMatchObject({ applicationId: 'a1', version: 3 });
      req.flush(null, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();

      expect(el.textContent).toContain('changed somewhere else');
    });
  });

  describe('PossibleDuplicates', () => {
    async function open(duplicates: PossibleDuplicate[] | 'error') {
      const fixture = TestBed.createComponent(PossibleDuplicates);
      fixture.componentRef.setInput('applicationId', 'a2');
      fixture.detectChanges();
      const req = http.expectOne('/api/applications/a2/possible-duplicates');
      if (duplicates === 'error') req.flush(null, { status: 500, statusText: 'Server Error' });
      else req.flush(duplicates);
      await fixture.whenStable();
      return fixture.nativeElement as HTMLElement;
    }

    it('warns about the same job pitched by another recruiter', async () => {
      const el = await open([
        {
          applicationId: 'a1',
          company: 'Acme',
          jobRole: 'Sr. Java Engineer',
          status: 'APPLIED',
          createdAt: '2026-10-02T12:00:00Z',
          recruiters: ['Pat Lee (Robert Half)'],
        },
      ]);

      expect(el.textContent).toContain('same job as');
      expect(el.querySelector('a')?.getAttribute('href')).toBe('/applications/a1');
      expect(el.querySelector('li')?.textContent).toContain('via Pat Lee (Robert Half)');
    });

    it('shows nothing when there are none, or the check fails', async () => {
      expect((await open([])).textContent?.trim()).toBe('');
      expect((await open('error')).textContent?.trim()).toBe('');
    });
  });
});
