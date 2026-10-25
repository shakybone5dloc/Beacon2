import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { AuthState } from './auth-state';
import { authGuard, guestGuard, safeReturnUrl } from './auth.guards';
import { authInterceptor } from './auth.interceptor';
import { CurrentUser } from './auth.model';

const USER: CurrentUser = { id: 'u1', email: 'me@example.com', displayName: null };

describe('auth', () => {
  let auth: AuthState;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthState);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function signIn(): void {
    auth.load().subscribe();
    http.expectOne('/api/auth/csrf').flush(null);
    http.expectOne('/api/auth/me').flush(USER);
  }

  describe('AuthState', () => {
    it('load primes the CSRF cookie, then remembers the signed-in user', () => {
      signIn();
      expect(auth.user()).toEqual(USER);
      expect(auth.signedIn()).toBe(true);
    });

    it('load treats a 401 from /me as signed out, without redirecting', () => {
      const navigate = vi.spyOn(router, 'navigate');
      auth.load().subscribe();
      http.expectOne('/api/auth/csrf').flush(null);
      http.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.signedIn()).toBe(false);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('login fetches a CSRF token, then posts the credentials', () => {
      auth.login({ email: 'me@example.com', password: 'pw' }).subscribe();
      http.expectOne('/api/auth/csrf').flush(null);
      const req = http.expectOne('/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ email: 'me@example.com', password: 'pw' });
      req.flush(USER);

      expect(auth.user()).toEqual(USER);
    });

    it('logout forgets the user even when the server call fails', () => {
      signIn();
      auth.logout().subscribe();
      http.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

      expect(auth.signedIn()).toBe(false);
    });
  });

  describe('guards', () => {
    const run = (guard: typeof authGuard, url = '/jobs') =>
      TestBed.runInInjectionContext(() => guard({} as never, { url } as never));

    it('authGuard sends signed-out users to sign in, remembering where they were going', () => {
      const result = run(authGuard) as UrlTree;
      expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fjobs');
    });

    it('authGuard lets signed-in users through', () => {
      signIn();
      expect(run(authGuard)).toBe(true);
    });

    it('guestGuard sends signed-in users to the board', () => {
      signIn();
      expect(router.serializeUrl(run(guestGuard) as UrlTree)).toBe('/');
    });

    it('safeReturnUrl only allows paths inside the app', () => {
      expect(safeReturnUrl('/applications/1')).toBe('/applications/1');
      expect(safeReturnUrl('https://evil.example')).toBe('/');
      expect(safeReturnUrl('//evil.example')).toBe('/');
      expect(safeReturnUrl('/\\evil.example')).toBe('/');
      expect(safeReturnUrl(undefined)).toBe('/');
    });
  });

  describe('authInterceptor', () => {
    it('a 401 from the API signs the user out and goes to sign in', () => {
      signIn();
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      TestBed.inject(HttpClient)
        .get('/api/applications')
        .subscribe({ error: () => {} });
      http.expectOne('/api/applications').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.signedIn()).toBe(false);
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/' } });
    });

    it('a failed sign-in is left to the sign-in page', () => {
      const navigate = vi.spyOn(router, 'navigate');

      TestBed.inject(HttpClient)
        .post('/api/auth/login', {})
        .subscribe({ error: () => {} });
      http.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(navigate).not.toHaveBeenCalled();
    });
  });
});
