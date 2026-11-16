import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { LoginPage } from './login-page';
import { RegisterPage } from './register-page';

describe('sign-in pages', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('LoginPage signs in and goes to the return URL', async () => {
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('returnUrl', '/jobs');
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'correct horse battery');
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({
      email: 'me@example.com',
      password: 'correct horse battery',
      rememberMe: true,
    });
    req.flush({ id: 'u1', email: 'me@example.com', displayName: null });

    expect(navigate).toHaveBeenCalledWith('/jobs');
  });

  it('LoginPage shows the server message when the password is wrong', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'wrong');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/auth/csrf').flush(null);
    http
      .expectOne('/api/auth/login')
      .flush(
        { detail: 'Email or password is incorrect.' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('Email or password is incorrect.');
  });

  /** Opens the sign-up page, answering whether it needs an invite code. */
  async function openRegister(inviteOnly: boolean, invite?: string) {
    const fixture = TestBed.createComponent(RegisterPage);
    if (invite) fixture.componentRef.setInput('invite', invite);
    fixture.detectChanges();
    http.expectOne('/api/auth/registration').flush({ inviteOnly });
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('RegisterPage checks the password length before sending anything', async () => {
    const fixture = await openRegister(false);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'short');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(el.textContent).toContain('At least 12 characters.');
  });

  it('RegisterPage shows a taken email next to the email field', async () => {
    const fixture = await openRegister(false);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.invite-code')).toBeNull();

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'correct horse battery');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      email: 'me@example.com',
      password: 'correct horse battery',
      displayName: null,
      inviteCode: null,
    });
    req.flush(
      { errors: { email: ['An account with this email already exists.'] } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el.textContent).toContain('An account with this email already exists.');
  });

  it('RegisterPage asks for an invite code while invite-only, filled in from the link', async () => {
    const fixture = await openRegister(true, 'K7QX-M2PA-9DTR');
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('invite-only for now');
    expect(el.querySelector<HTMLInputElement>('.invite-code')!.value).toBe('K7QX-M2PA-9DTR');

    type(el, 'input[type=email]', 'pat@example.com');
    type(el, 'input[type=password]', 'correct horse battery');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body.inviteCode).toBe('K7QX-M2PA-9DTR');
    req.flush(
      { errors: { inviteCode: ["That invite code isn't valid, or it has already been used."] } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el.textContent).toContain("That invite code isn't valid");
  });

  it('RegisterPage needs the code before sending anything while invite-only', async () => {
    const fixture = await openRegister(true);
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'pat@example.com');
    type(el, 'input[type=password]', 'correct horse battery');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    http.expectNone('/api/auth/register');
    expect(el.textContent).toContain('Required.');
  });

  it('LoginPage says the account was deleted, after deleting it', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('deleted', '1');
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.deleted')?.textContent).toContain(
      'Your account and all its data were deleted.',
    );
  });

  it('LoginPage says when there have been too many attempts', async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'wrong password!');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/auth/csrf').flush(null);
    http
      .expectOne('/api/auth/login')
      .flush(
        { detail: 'Too many attempts. Try again in 12 minutes.' },
        { status: 429, statusText: 'Too Many Requests' },
      );
    await fixture.whenStable();

    expect(el.textContent).toContain('Too many attempts. Try again in 12 minutes.');
  });
});
