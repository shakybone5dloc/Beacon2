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
    sendFrom: 'job.hunt@gmail.com',
    gmail: true,
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
    expect(composeUrl('GMAIL', 'pat@x.example', 'Hi', 'Body', 'job.hunt@example.test')).toBe(
      'https://mail.google.com/mail/?authuser=job.hunt%40example.test&view=cm&fs=1&to=pat%40x.example&su=Hi&body=Body',
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

  it('starts addressed to the first person with an email, and opens Gmail as the send-from account', async () => {
    const { el, form } = await open(aCheckIn());

    expect(form.querySelector<HTMLSelectElement>('select[formControlName=to]')!.value).toBe('p1');
    expect(form.querySelector<HTMLInputElement>('.address')!.value).toBe('pat@elevance.example');
    expect(form.querySelector<HTMLTextAreaElement>('.message')!.value).toMatch(/^Hello Pat,/);
    expect(el.querySelector('.send-from')?.textContent).toBe('job.hunt@gmail.com');
    const gmail = el.querySelector<HTMLAnchorElement>('a.open-gmail')!;
    expect(gmail.textContent).toContain('Open in Gmail');
    expect(gmail.href).toContain('https://mail.google.com/mail/?authuser=job.hunt%40gmail.com&view=cm');
    expect(gmail.href).toContain('to=pat%40elevance.example');
    expect(gmail.href).toContain('su=Checking%20in');
    expect(gmail.target).toBe('_blank');
    // The mail app is still there, second.
    const mailApp = el.querySelector<HTMLAnchorElement>('a.open-mail-app')!;
    expect(mailApp.textContent).toContain('Use my mail app');
    expect(mailApp.href).toMatch(/^mailto:pat%40elevance\.example\?subject=/);
    expect(mailApp.classList).not.toContain('btn-primary');
  });

  it('offers only the mail app when the send-from address is not Gmail', async () => {
    const { el } = await open(aCheckIn({ sendFrom: 'me@work.example', gmail: false }));

    expect(el.querySelector('a.open-gmail')).toBeNull();
    const mailApp = el.querySelector<HTMLAnchorElement>('a.open-mail-app')!;
    expect(mailApp.textContent).toContain('Open in my mail app');
    expect(mailApp.classList).toContain('btn-primary');
    expect(el.textContent).toContain('in your mail app, to send from');
    expect(el.querySelector('.send-from')?.textContent).toBe('me@work.example');
  });

  it('goes to a team when nobody on the application has an email', async () => {
    const { fixture, el, form } = await open(aCheckIn({ recipients: [], gmail: false }));

    expect(form.querySelector<HTMLSelectElement>('select[formControlName=to]')!.value).toBe('__team');
    expect(form.querySelector<HTMLInputElement>('.team-name')!.value).toBe('Hiring team');
    expect(form.querySelector<HTMLTextAreaElement>('.message')!.value).toMatch(/^Hello,/);
    set(form.querySelector<HTMLInputElement>('.address')!, 'careers@elevance.example');
    fixture.detectChanges();
    expect(el.querySelector<HTMLAnchorElement>('a.open-mail-app')!.href).toMatch(
      /^mailto:careers%40elevance\.example\?subject=/,
    );
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
    const link = el.querySelector<HTMLAnchorElement>('a.open-gmail')!;
    // Don't let the test page navigate.
    link.addEventListener('click', (e) => e.preventDefault());

    link.click();
    fixture.detectChanges();
    expect(el.querySelector('.sent')?.textContent).toContain('Did you send it?');
    // "Open it again" opens it the same way: in Gmail.
    expect(el.querySelector<HTMLAnchorElement>('a.reopen')!.href).toContain('mail.google.com');
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
    const link = el.querySelector<HTMLAnchorElement>('a.open-mail-app')!;
    link.addEventListener('click', (e) => e.preventDefault());
    link.click();
    fixture.detectChanges();

    expect(el.querySelector<HTMLAnchorElement>('a.reopen')!.href).toMatch(/^mailto:/);
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

  async function open(sendFrom: string, gmail: boolean) {
    const fixture = TestBed.createComponent(MailSettingsPage);
    fixture.detectChanges();
    http.expectOne('/api/me/send-from').flush({ sendFrom, signInEmail: 'me@example.test', gmail });
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('starts with the sign-in address and says it opens in the mail app', async () => {
    const { el } = await open('me@example.test', false);

    expect(el.querySelector<HTMLInputElement>('input.send-from')!.value).toBe('me@example.test');
    expect(el.querySelector('.how')?.textContent).toContain("this device's mail app");
    expect(el.querySelector<HTMLButtonElement>('button.save')!.disabled).toBe(true);
    expect(el.querySelector('.use-sign-in')).toBeNull();
  });

  it('saves a Gmail address and says Gmail opens as that account', async () => {
    const { fixture, el } = await open('me@example.test', false);
    const input = el.querySelector<HTMLInputElement>('input.send-from')!;

    input.value = '  Job.Hunt@gmail.com ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button.save')!.click();
    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/me/send-from');
    expect(req.request.body).toEqual({ sendFrom: 'Job.Hunt@gmail.com' });
    req.flush({ sendFrom: 'job.hunt@gmail.com', signInEmail: 'me@example.test', gmail: true });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(input.value).toBe('job.hunt@gmail.com');
    expect(el.querySelector('.how')?.textContent).toContain('emails open in Gmail as job.hunt@gmail.com');
    expect(el.querySelector('.saved')?.textContent).toContain('Saved');

    // Back to the sign-in address.
    el.querySelector<HTMLButtonElement>('.use-sign-in')!.click();
    const reset = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/me/send-from');
    expect(reset.request.body).toEqual({ sendFrom: null });
    reset.flush({ sendFrom: 'me@example.test', signInEmail: 'me@example.test', gmail: false });
  });

  it('says when the address is not one', async () => {
    const { fixture, el } = await open('me@example.test', false);
    const input = el.querySelector<HTMLInputElement>('input.send-from')!;

    input.value = 'not an address';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button.save')!.click();
    http.expectOne('/api/me/send-from').flush({}, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(el.querySelector('.notice')?.textContent).toContain("doesn't look like an email address");
  });
});
