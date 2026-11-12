import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { anApplication } from '../applications/testing';
import { MailSettingsPage } from '../settings/mail-settings-page';
import { CheckInEmail } from './check-in-email';
import { CheckIn, composeUrl, daysFromNow, greet } from './check-in.model';

function aCheckIn(overrides: Partial<CheckIn> = {}): CheckIn {
  return {
    recipients: [
      { personId: 'p1', name: 'Pat Lee', email: 'pat@elevance.example', role: 'RECRUITER' },
      { personId: 'p2', name: 'Sam Ortiz', email: null, role: 'HIRING_MANAGER' },
    ],
    defaultTeam: 'Hiring team',
    subject: 'Checking in: Staff Engineer application',
    body: 'Hello,\n\nI applied on November 3. Any update?\n\nThank you,\nTest',
    mailClient: 'GMAIL',
    aiAvailable: true,
    ...overrides,
  };
}

function set(input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement, value: string) {
  input.value = value;
  input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
}

describe('check-in.model', () => {
  it('builds a Gmail compose link or a mailto link', () => {
    expect(composeUrl('GMAIL', 'pat@x.example', 'Hi & bye', 'Line 1\nLine 2')).toBe(
      'https://mail.google.com/mail/?view=cm&fs=1&to=pat%40x.example&su=Hi%20%26%20bye&body=Line%201%0ALine%202',
    );
    expect(composeUrl('MAIL_APP', 'pat@x.example', 'Hi', 'Line 1\nLine 2')).toBe(
      'mailto:pat%40x.example?subject=Hi&body=Line%201%0D%0ALine%202',
    );
  });

  it('re-addresses a plain greeting and leaves a rewritten one alone', () => {
    expect(greet('Hello,\n\nBody', 'Pat Lee')).toBe('Hello Pat,\n\nBody');
    expect(greet('Hello Pat,\n\nBody', 'Sam Ortiz')).toBe('Hello Sam,\n\nBody');
    expect(greet('Hello Pat,\n\nBody', null)).toBe('Hello,\n\nBody');
    expect(greet('Dear team,\n\nBody', 'Pat Lee')).toBe('Dear team,\n\nBody');
  });

  it('counts days ahead in the local calendar', () => {
    expect(daysFromNow(7, new Date(2026, 10, 28))).toBe('2026-12-05');
  });
});

describe('CheckInEmail', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(checkIn: CheckIn) {
    const fixture = TestBed.createComponent(CheckInEmail);
    fixture.componentRef.setInput('applicationId', 'a1');
    const logged = vi.fn();
    fixture.componentInstance.logged.subscribe(logged);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/applications/a1/check-in');
    expect(req.request.params.get('zone')).toBeTruthy();
    req.flush(checkIn);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, logged, el, form: el.querySelector<HTMLFormElement>('form')! };
  }

  it('starts addressed to the first person with an email, and opens in the chosen mail', async () => {
    const { el, form } = await open(aCheckIn());

    expect(form.querySelector<HTMLSelectElement>('select[formControlName=to]')!.value).toBe('p1');
    expect(form.querySelector<HTMLInputElement>('.address')!.value).toBe('pat@elevance.example');
    expect(form.querySelector<HTMLTextAreaElement>('.message')!.value).toMatch(/^Hello Pat,/);
    const link = el.querySelector<HTMLAnchorElement>('a.open')!;
    expect(link.textContent).toContain('Open in Gmail');
    expect(link.href).toContain('https://mail.google.com/mail/?view=cm');
    expect(link.href).toContain('to=pat%40elevance.example');
    expect(link.href).toContain('su=Checking%20in');
    expect(link.target).toBe('_blank');
  });

  it('goes to a team when nobody on the application has an email', async () => {
    const { fixture, el, form } = await open(aCheckIn({ recipients: [], mailClient: 'MAIL_APP' }));

    expect(form.querySelector<HTMLSelectElement>('select[formControlName=to]')!.value).toBe('__team');
    expect(form.querySelector<HTMLInputElement>('.team-name')!.value).toBe('Hiring team');
    expect(form.querySelector<HTMLTextAreaElement>('.message')!.value).toMatch(/^Hello,/);
    set(form.querySelector<HTMLInputElement>('.address')!, 'careers@elevance.example');
    fixture.detectChanges();
    expect(el.querySelector<HTMLAnchorElement>('a.open')!.href).toMatch(
      /^mailto:careers%40elevance\.example\?subject=/,
    );
    expect(el.querySelector('a.open')?.textContent).toContain('Open in my mail app');
  });

  it('drafts with AI from what is in the form', async () => {
    const { fixture, el, form } = await open(aCheckIn());

    el.querySelector<HTMLButtonElement>('.draft')!.click();
    const req = http.expectOne((r) => r.url === '/api/applications/a1/check-in/draft');
    expect(req.request.body.recipient).toBe('Pat Lee');
    expect(req.request.body.body).toMatch(/^Hello Pat,/);
    req.flush({ subject: 'Following up', body: 'Hello Pat,\n\nAny news?' });
    fixture.detectChanges();

    expect(form.querySelector<HTMLInputElement>('.subject')!.value).toBe('Following up');
    expect(form.querySelector<HTMLTextAreaElement>('.message')!.value).toBe('Hello Pat,\n\nAny news?');
  });

  it('points to API keys when there is no key to draft with', async () => {
    const { el } = await open(aCheckIn({ aiAvailable: false }));

    expect(el.querySelector('.draft')).toBeNull();
    expect(el.querySelector('.no-ai a')?.getAttribute('href')).toBe('/settings/api-keys');
  });

  it('after opening, logs the email to the team and sets the follow-up', async () => {
    const { fixture, logged, el, form } = await open(aCheckIn({ recipients: [] }));
    const link = el.querySelector<HTMLAnchorElement>('a.open')!;
    // Don't let the test page navigate.
    link.addEventListener('click', (e) => e.preventDefault());

    link.click();
    fixture.detectChanges();
    expect(el.querySelector('.sent')?.textContent).toContain('Did you send it?');
    set(form.querySelector<HTMLInputElement>('.follow-up-on')!, '2026-11-19');
    el.querySelector<HTMLButtonElement>('.log')!.click();

    const contact = http.expectOne((r) => r.method === 'POST' && r.url === '/api/contacts');
    expect(contact.request.body).toMatchObject({
      personId: null,
      team: 'Hiring team',
      applicationId: 'a1',
      channel: 'EMAIL',
      direction: 'OUTBOUND',
    });
    expect(contact.request.body.body).toMatch(/^Subject: Checking in: Staff Engineer application\n\nHello,/);
    contact.flush({});
    http.expectOne('/api/applications/a1').flush(anApplication({ id: 'a1', version: 4 }));
    const followUp = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/applications/a1/follow-up');
    expect(followUp.request.body).toEqual({ followUpOn: '2026-11-19', version: 4 });
    followUp.flush(anApplication({ id: 'a1' }));

    expect(logged).toHaveBeenCalled();
  });

  it('logs to the person without a follow-up when that is unticked', async () => {
    const { fixture, logged, el, form } = await open(aCheckIn());
    const link = el.querySelector<HTMLAnchorElement>('a.open')!;
    link.addEventListener('click', (e) => e.preventDefault());
    link.click();
    fixture.detectChanges();

    form.querySelector<HTMLInputElement>('input[formControlName=followUp]')!.click();
    el.querySelector<HTMLButtonElement>('.log')!.click();
    const contact = http.expectOne((r) => r.method === 'POST' && r.url === '/api/contacts');
    expect(contact.request.body).toMatchObject({ personId: 'p1', team: null });
    contact.flush({});

    expect(logged).toHaveBeenCalled();
  });
});

describe('MailSettingsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows where email opens and saves a new choice', async () => {
    const fixture = TestBed.createComponent(MailSettingsPage);
    fixture.detectChanges();
    http.expectOne('/api/me/mail-client').flush({ mailClient: 'GMAIL' });
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const radios = Array.from(el.querySelectorAll<HTMLInputElement>('input[type=radio]'));
    expect(radios.map((r) => r.checked)).toEqual([true, false]);

    radios[1].click();
    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/me/mail-client');
    expect(req.request.body).toEqual({ mailClient: 'MAIL_APP' });
    req.flush({ mailClient: 'MAIL_APP' });
    fixture.detectChanges();

    expect(el.querySelector('.saved')?.textContent).toContain('Saved');
  });
});
