import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { DigestSettingsPage } from './digest-settings-page';
import { DigestSettings, hourLabel } from './digest.model';

function someSettings(overrides: Partial<DigestSettings> = {}): DigestSettings {
  return {
    enabled: false,
    dayOfWeek: 'MONDAY',
    hour: 8,
    zone: 'America/Chicago',
    email: 'me@example.com',
    lastSentAt: null,
    nextSendAt: null,
    mailConfigured: true,
    version: 0,
    ...overrides,
  };
}

it('labels hours on a 12-hour clock', () => {
  expect([0, 8, 12, 17].map(hourLabel)).toEqual(['12 AM', '8 AM', '12 PM', '5 PM']);
});

describe('DigestSettingsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(settings: DigestSettings) {
    const fixture = TestBed.createComponent(DigestSettingsPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/me/digest').flush(settings);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('turns the weekly email on, saving with the browser zone and version', async () => {
    const { fixture, el } = await open(someSettings());
    expect(el.querySelector('.status')?.textContent).toContain('Off.');

    const toggle = el.querySelector<HTMLInputElement>('.enabled input')!;
    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));

    const req = http.expectOne((r) => r.url === '/api/me/digest' && r.method === 'PUT');
    expect(req.request.body).toMatchObject({
      enabled: true,
      dayOfWeek: 'MONDAY',
      hour: 8,
      version: 0,
    });
    expect(typeof req.request.body.zone).toBe('string');
    req.flush(someSettings({ enabled: true, version: 1, nextSendAt: '2026-11-09T08:00:00-06:00' }));
    await fixture.whenStable();

    expect(el.querySelector('.status')?.textContent).toContain('Next one goes to me@example.com');
    expect(el.querySelector('.saved')).toBeTruthy();

    const hour = el.querySelector<HTMLSelectElement>('select.hour')!;
    hour.value = '17';
    hour.dispatchEvent(new Event('change'));
    const second = http.expectOne((r) => r.method === 'PUT');
    expect(second.request.body).toMatchObject({ enabled: true, hour: 17, version: 1 });
    second.flush(someSettings({ enabled: true, hour: 17, version: 2 }));
  });

  it('previews the email in a sandboxed frame and sends one now', async () => {
    const { fixture, el } = await open(someSettings());

    el.querySelector<HTMLButtonElement>('.preview')!.click();
    http
      .expectOne((r) => r.url === '/api/me/digest/preview')
      .flush({ subject: 'Beacon: 1 needs attention', html: '<p>Hi</p>', text: 'Hi' });
    await fixture.whenStable();

    expect(el.querySelector('.preview-subject')?.textContent).toContain(
      'Beacon: 1 needs attention',
    );
    const frame = el.querySelector('iframe')!;
    expect(frame.getAttribute('sandbox')).toBe('');
    expect(frame.getAttribute('srcdoc')).toBe('<p>Hi</p>');

    el.querySelector<HTMLButtonElement>('.send')!.click();
    const send = http.expectOne((r) => r.url === '/api/me/digest/send');
    expect(send.request.method).toBe('POST');
    send.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(el.querySelector('.sent')?.textContent).toContain('Sent to me@example.com');
  });

  it("shows the mail server's reason when sending fails", async () => {
    const { fixture, el } = await open(someSettings());

    el.querySelector<HTMLButtonElement>('.send')!.click();
    // A call with no success body is read as text, so the problem arrives as a JSON string.
    http
      .expectOne((r) => r.url === '/api/me/digest/send')
      .flush(
        JSON.stringify({
          title: 'Could not send the digest',
          detail: "The mail server didn't accept the email: 550 testing emails only",
        }),
        { status: 502, statusText: 'Bad Gateway' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('550 testing emails only');
  });

  it('says when the server cannot send email, and still allows a preview', async () => {
    const { el } = await open(someSettings({ mailConfigured: false }));

    expect(el.querySelector('.no-mail')?.textContent).toContain("Email isn't set up");
    expect(el.querySelector<HTMLButtonElement>('.send')!.disabled).toBe(true);
    expect(el.querySelector<HTMLButtonElement>('.preview')!.disabled).toBe(false);
  });
});
