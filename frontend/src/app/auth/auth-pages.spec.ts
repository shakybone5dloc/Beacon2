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
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

    http.expectOne('/api/auth/csrf').flush(null);
    http
      .expectOne('/api/auth/login')
      .flush({ id: 'u1', email: 'me@example.com', displayName: null });

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

  it('RegisterPage checks the password length before sending anything', async () => {
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'short');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(el.textContent).toContain('At least 12 characters.');
  });

  it('RegisterPage shows a taken email next to the email field', async () => {
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    type(el, 'input[type=email]', 'me@example.com');
    type(el, 'input[type=password]', 'correct horse battery');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/auth/csrf').flush(null);
    const req = http.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      email: 'me@example.com',
      password: 'correct horse battery',
      displayName: null,
    });
    req.flush(
      { errors: { email: ['An account with this email already exists.'] } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(el.textContent).toContain('An account with this email already exists.');
  });
});
