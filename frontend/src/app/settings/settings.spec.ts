import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthState } from '../auth/auth-state';
import { AccountPage } from './account-page';
import { Invite, inviteLink } from './invites-api';
import { InvitesPage } from './invites-page';
import { SettingsLayout } from './settings-layout';

function anInvite(overrides: Partial<Invite> = {}): Invite {
  return {
    id: 'i1',
    code: 'K7QX-M2PA-9DTR',
    note: 'Pat, from LinkedIn',
    state: 'OPEN',
    createdAt: '2026-11-14T12:00:00Z',
    expiresAt: '2026-11-28T12:00:00Z',
    usedBy: null,
    usedAt: null,
    revokedAt: null,
    ...overrides,
  };
}

describe('settings', () => {
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

  /** Signs in as a user who is, or isn't, an admin. */
  function signIn(admin: boolean): void {
    TestBed.inject(AuthState).load().subscribe();
    http.expectOne('/api/auth/csrf').flush(null);
    http
      .expectOne('/api/auth/me')
      .flush({ id: 'u1', email: 'me@example.com', displayName: null, admin });
  }

  it('groups the settings pages and shows Admin only to admins', async () => {
    signIn(false);
    let fixture = TestBed.createComponent(SettingsLayout);
    fixture.detectChanges();
    let el = fixture.nativeElement as HTMLElement;
    const links = () => Array.from(el.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(links()).toEqual([
      '/settings/account',
      '/settings/digest',
      '/settings/api-keys',
      '/providers',
    ]);

    signIn(true);
    fixture = TestBed.createComponent(SettingsLayout);
    fixture.detectChanges();
    el = fixture.nativeElement as HTMLElement;
    expect(links()).toContain('/settings/invites');
  });

  it('builds the sign-up link for a code', () => {
    expect(inviteLink('K7QX-M2PA-9DTR', 'https://beacon.example')).toBe(
      'https://beacon.example/register?invite=K7QX-M2PA-9DTR',
    );
  });

  describe('AccountPage', () => {
    async function open() {
      signIn(false);
      const fixture = TestBed.createComponent(AccountPage);
      fixture.detectChanges();
      http
        .expectOne('/api/me/send-from')
        .flush({ sendFrom: 'me@example.com', signInEmail: 'me@example.com', gmail: false });
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    function fill(
      fixture: { detectChanges(): void },
      el: HTMLElement,
      password: string,
      understood: boolean,
    ) {
      const input = el.querySelector<HTMLInputElement>('.password')!;
      input.value = password;
      input.dispatchEvent(new Event('input'));
      if (understood) el.querySelector<HTMLInputElement>('.understood')!.click();
      fixture.detectChanges();
    }

    it('holds the send-from address with the rest of the account', async () => {
      const { fixture, el } = await open();
      fixture.detectChanges();

      expect(el.querySelector<HTMLInputElement>('input.send-from')!.value).toBe('me@example.com');
    });

    it('links the export download', async () => {
      const { el } = await open();

      const link = el.querySelector<HTMLAnchorElement>('a.export')!;
      expect(link.getAttribute('href')).toBe('/api/me/export');
      expect(link.hasAttribute('download')).toBe(true);
      expect(el.textContent).toContain('me@example.com');
    });

    it('deletes only with the password and the box ticked, then signs out', async () => {
      const { fixture, el } = await open();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
      const button = el.querySelector<HTMLButtonElement>('button.delete')!;
      expect(button.disabled).toBe(true);
      fill(fixture, el, 'correct horse battery', false);
      expect(button.disabled).toBe(true);
      el.querySelector<HTMLInputElement>('.understood')!.click();
      fixture.detectChanges();
      expect(button.disabled).toBe(false);

      button.click();
      const req = http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/me');
      expect(req.request.body).toEqual({ password: 'correct horse battery' });
      req.flush(null, { status: 204, statusText: 'No Content' });

      expect(TestBed.inject(AuthState).user()).toBeNull();
      expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { deleted: 1 } });
    });

    it('says when the password is wrong', async () => {
      const { fixture, el } = await open();
      fill(fixture, el, 'not my password', true);

      el.querySelector<HTMLButtonElement>('button.delete')!.click();
      http
        .expectOne('/api/me')
        .flush(
          { errors: { password: ["That password isn't right."] } },
          { status: 400, statusText: 'Bad Request' },
        );
      fixture.detectChanges();

      expect(el.querySelector('.password-error')?.textContent).toContain(
        "That password isn't right.",
      );
      expect(TestBed.inject(AuthState).user()).not.toBeNull();
    });
  });

  describe('InvitesPage', () => {
    async function open(invites: Invite[]) {
      signIn(true);
      const fixture = TestBed.createComponent(InvitesPage);
      fixture.detectChanges();
      http.expectOne('/api/admin/invites').flush(invites);
      await fixture.whenStable();
      fixture.detectChanges();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('lists codes with who they are for and what became of them', async () => {
      const { el } = await open([
        anInvite(),
        anInvite({
          id: 'i2',
          code: 'AAAA-BBBB-CCCC',
          state: 'USED',
          usedBy: 'sam@example.com',
          usedAt: '2026-11-15T12:00:00Z',
        }),
      ]);

      const items = el.querySelectorAll('.invite');
      expect(items[0].querySelector('.code')?.textContent).toBe('K7QX-M2PA-9DTR');
      expect(items[0].textContent).toContain('Pat, from LinkedIn');
      expect(items[0].querySelector('.state')?.textContent).toBe('Not used yet');
      expect(items[0].querySelector<HTMLInputElement>('.link')!.value).toContain(
        '/register?invite=K7QX-M2PA-9DTR',
      );
      expect(items[1].querySelector('.state')?.textContent).toBe('Used');
      expect(items[1].textContent).toContain('Made sam@example.com');
      expect(items[1].querySelector('.revoke')).toBeNull();
    });

    it('makes a code for someone and puts it first', async () => {
      const { fixture, el } = await open([anInvite()]);
      const note = el.querySelector<HTMLInputElement>('.note')!;
      note.value = ' Sam ';
      note.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/admin/invites');
      expect(req.request.body).toEqual({ note: 'Sam', days: 14 });
      req.flush(anInvite({ id: 'i3', code: 'NEWC-ODE2-3456', note: 'Sam' }));
      fixture.detectChanges();

      expect(el.querySelector('.invite .code')?.textContent).toBe('NEWC-ODE2-3456');
    });

    it('revokes after asking', async () => {
      const { fixture, el } = await open([anInvite()]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      el.querySelector<HTMLButtonElement>('.revoke')!.click();
      http
        .expectOne((r) => r.method === 'POST' && r.url === '/api/admin/invites/i1/revoke')
        .flush(anInvite({ state: 'REVOKED', revokedAt: '2026-11-14T13:00:00Z' }));
      fixture.detectChanges();

      expect(el.querySelector('.state')?.textContent).toBe('Revoked');
      expect(el.querySelector('.revoke')).toBeNull();
    });
  });
});
