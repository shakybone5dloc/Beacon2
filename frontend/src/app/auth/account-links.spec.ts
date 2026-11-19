import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthState } from './auth-state';
import { ConfirmEmailBanner } from './confirm-email-banner';
import { ConfirmEmailPage } from './confirm-email-page';
import { ForgotPasswordPage } from './forgot-password-page';
import { LoginPage } from './login-page';
import { ResetPasswordPage } from './reset-password-page';

describe('password reset and email confirmation', () => {
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

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(el: HTMLElement): void {
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
  }

  function signIn(emailConfirmationNeeded: boolean): void {
    TestBed.inject(AuthState).load().subscribe();
    http.expectOne('/api/auth/csrf').flush(null);
    http.expectOne('/api/auth/me').flush({
      id: 'u1',
      email: 'me@example.com',
      displayName: null,
      admin: false,
      emailConfirmationNeeded,
    });
  }

  it('sign-in offers a reset only when the server can email, and says when one just happened', async () => {
    let fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    http.expectOne('/api/auth/registration').flush({ inviteOnly: false, passwordReset: false });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('a.forgot')).toBeNull();

    fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('reset', '1');
    await fixture.whenStable();
    http.expectOne('/api/auth/registration').flush({ inviteOnly: false, passwordReset: true });
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('a.forgot')?.getAttribute('href')).toBe('/forgot-password');
    expect(el.querySelector('.reset-done')?.textContent).toContain('password was changed');
  });

  it('asking for a link says the same thing whatever the server knows', async () => {
    const fixture = TestBed.createComponent(ForgotPasswordPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', '  me@example.com ');
    submit(el);
    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/password-reset');
    expect(req.request.body).toEqual({ email: 'me@example.com' });
    req.flush(null, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();

    expect(el.querySelector('.sent')?.textContent).toContain(
      'If me@example.com has a Beacon account',
    );
    expect(el.querySelector('form')).toBeNull();
  });

  it('a reset link sets the new password and goes to sign-in', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.componentRef.setInput('token', 'abc123');
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    // Too short: caught before anything is sent.
    type(el, 'input[type=password]', 'short');
    submit(el);
    await fixture.whenStable();
    expect(el.querySelector('.error')?.textContent).toContain('At least 12 characters');

    type(el, 'input[type=password]', 'a brand new passphrase');
    submit(el);
    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/password-reset/confirm');
    expect(req.request.body).toEqual({ token: 'abc123', password: 'a brand new passphrase' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reset: 1 } });
  });

  it('an expired reset link says so and offers a new one', async () => {
    const fixture = TestBed.createComponent(ResetPasswordPage);
    fixture.componentRef.setInput('token', 'old');
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=password]', 'a brand new passphrase');
    submit(el);
    http.expectOne('/api/auth/csrf').flush(null);
    http
      .expectOne('/api/auth/password-reset/confirm')
      .flush(
        JSON.stringify({ detail: 'This link has expired or was already used. Ask for a new one.' }),
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('expired or was already used');
    expect(el.querySelector('.notice a')?.getAttribute('href')).toBe('/forgot-password');
  });

  it('a confirmation link confirms right away and stops the banner', async () => {
    signIn(true);
    const banner = TestBed.createComponent(ConfirmEmailBanner);
    await banner.whenStable();
    expect((banner.nativeElement as HTMLElement).querySelector('.confirm-banner')).not.toBeNull();

    const fixture = TestBed.createComponent(ConfirmEmailPage);
    fixture.componentRef.setInput('token', 'tok');
    await fixture.whenStable();
    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/confirm-email');
    expect(req.request.body).toEqual({ token: 'tok' });
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    await banner.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.done')?.textContent).toContain(
      'confirmed',
    );
    expect((banner.nativeElement as HTMLElement).querySelector('.confirm-banner')).toBeNull();
  });

  it('the banner resends the link, and says when there have been too many', async () => {
    signIn(true);
    const fixture = TestBed.createComponent(ConfirmEmailBanner);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('me@example.com');

    el.querySelector<HTMLButtonElement>('button.resend')!.click();
    http
      .expectOne('/api/me/confirm-email')
      .flush(JSON.stringify({ detail: 'Too many attempts. Try again in 40 minutes.' }), {
        status: 429,
        statusText: 'Too Many Requests',
      });
    await fixture.whenStable();
    expect(el.textContent).toContain('Try again in 40 minutes');

    el.querySelector<HTMLButtonElement>('button.resend')!.click();
    http.expectOne('/api/me/confirm-email').flush(null, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    expect(el.querySelector('.sent')?.textContent).toContain('Sent');
  });

  it('the banner stays away once the email is confirmed', async () => {
    signIn(false);
    const fixture = TestBed.createComponent(ConfirmEmailBanner);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.confirm-banner')).toBeNull();
  });
});
