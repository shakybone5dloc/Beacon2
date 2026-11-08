import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { App, menuIsActive } from './app';
import { AuthState } from './auth/auth-state';

describe('App', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('shows only the brand when signed out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.brand')?.textContent).toContain('Beacon');
    expect(el.querySelector('nav')).toBeNull();
  });

  it('shows the nav and who is signed in', async () => {
    TestBed.inject(AuthState).load().subscribe();
    http.expectOne('/api/auth/csrf').flush(null);
    http.expectOne('/api/auth/me').flush({ id: 'u1', email: 'me@example.com', displayName: null });

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    http.expectOne('/api/matches/summary').flush({ newCount: 3 });
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('nav a')?.getAttribute('href')).toBe('/');
    expect(el.querySelector('.who')?.textContent).toContain('me@example.com');
    const nav = el.querySelector('nav')!;
    expect(
      Array.from(nav.querySelectorAll(':scope > a, :scope > button')).map((e) =>
        e.textContent!.replace('expand_more', '').replace(/\d+/, '').trim(),
      ),
    ).toEqual(['Home', 'Board', 'Jobs', 'Network', 'Prep', 'Stats']);
    // The new-matches count shows on the Jobs menu, without opening it.
    expect(nav.querySelector('.nav-menu .match-count')?.textContent?.trim()).toBe('3');
  });

  it('opens a group menu with its pages', async () => {
    TestBed.inject(AuthState).load().subscribe();
    http.expectOne('/api/auth/csrf').flush(null);
    http.expectOne('/api/auth/me').flush({ id: 'u1', email: 'me@example.com', displayName: null });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    http.expectOne('/api/matches/summary').flush({ newCount: 2 });
    await fixture.whenStable();

    const jobs = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('nav .nav-menu'),
    ).find((b) => b.textContent?.includes('Jobs'))!;
    jobs.click();
    await fixture.whenStable();

    // CDK menus open in an overlay outside the component.
    const items = Array.from(document.querySelectorAll('.cdk-overlay-container .menu-item'));
    expect(items.map((a) => a.getAttribute('href'))).toEqual(['/jobs', '/matches', '/searches']);
    expect(items[1].querySelector('.match-count')?.textContent?.trim()).toBe('2');
  });
});

describe('menuIsActive', () => {
  const prep = [
    { path: '/stories', label: 'Stories' },
    { path: '/study', label: 'Study' },
  ];

  it("is active on a menu's pages and below them, ignoring filters", () => {
    expect(menuIsActive(prep, '/study')).toBe(true);
    expect(menuIsActive(prep, '/study/d1/session')).toBe(true);
    expect(menuIsActive(prep, '/stories?tag=auth')).toBe(true);
    expect(menuIsActive(prep, '/studying')).toBe(false);
    expect(menuIsActive(prep, '/')).toBe(false);
  });
});
