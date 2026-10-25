import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ApiKeysPage } from './api-keys-page';
import { ProviderGuide } from './provider-guide';
import { accessLabel, ApiKeyStatus, Provider } from './provider.model';

function aProvider(overrides: Partial<Provider> = {}): Provider {
  return {
    id: 'remotive',
    name: 'Remotive',
    kind: 'JOB_SOURCE',
    cost: 'FREE',
    needsKey: false,
    turnedOff: false,
    coverage: 'Remote jobs.',
    signUpUrl: 'https://remotive.com',
    pricing: 'Free.',
    steps: [],
    fields: [],
    ...overrides,
  };
}

const OPENAI = aProvider({
  id: 'openai',
  name: 'OpenAI',
  kind: 'AI',
  cost: 'PAID',
  needsKey: true,
  fields: [{ name: 'apiKey', label: 'API key', secret: true, required: true }],
});
const USAJOBS = aProvider({
  id: 'usajobs',
  name: 'USAJOBS',
  needsKey: true,
  fields: [
    { name: 'apiKey', label: 'API key', secret: true, required: true },
    { name: 'email', label: 'Email the key was sent to', secret: false, required: true },
  ],
});
const NOT_SAVED = (provider: string): ApiKeyStatus => ({
  provider,
  saved: false,
  hint: null,
  updatedAt: null,
});

describe('providers', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('accessLabel tells free, keyed and paid apart', () => {
    expect(accessLabel(aProvider())).toBe('Free · no key');
    expect(accessLabel(USAJOBS)).toBe('Free · key needed');
    expect(
      accessLabel(
        aProvider({
          fields: [{ name: 'apiKey', label: 'API key', secret: true, required: false }],
        }),
      ),
    ).toBe('Free · key optional');
    expect(accessLabel(OPENAI)).toBe('Paid');
  });

  it('ProviderGuide shows a card per provider, with Add key only where a key is taken', async () => {
    const fixture = TestBed.createComponent(ProviderGuide);
    http.expectOne('/api/providers').flush([aProvider(), USAJOBS, OPENAI]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelectorAll('.card').length).toBe(3);
    const remotive = el.querySelector('#remotive')!;
    expect(remotive.querySelector('.add')).toBeNull();
    const usajobs = el.querySelector('#usajobs')!;
    expect(usajobs.textContent).toContain('Free · key needed');
    expect(usajobs.querySelector('.add')?.getAttribute('href')).toBe('/settings/api-keys#usajobs');
  });

  describe('ApiKeysPage', () => {
    async function open(keys: ApiKeyStatus[]) {
      const fixture = TestBed.createComponent(ApiKeysPage);
      fixture.detectChanges();
      http.expectOne('/api/providers').flush([aProvider(), USAJOBS, OPENAI]);
      http.expectOne('/api/me/api-keys').flush(keys);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    function type(input: HTMLInputElement, value: string): void {
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }

    it('lists only providers that take a key, with a form for each unsaved one', async () => {
      const { el } = await open([NOT_SAVED('usajobs'), NOT_SAVED('openai')]);

      expect(el.querySelector('#remotive')).toBeNull();
      const usajobs = el.querySelector('#usajobs')!;
      const inputs = usajobs.querySelectorAll('input');
      expect(inputs.length).toBe(2);
      expect(inputs[0].type).toBe('password');
      expect(inputs[1].type).toBe('text');
    });

    it('saves the fields, then shows the key as added without its value', async () => {
      const { fixture, el } = await open([NOT_SAVED('usajobs'), NOT_SAVED('openai')]);
      const openai = el.querySelector('#openai')!;

      type(openai.querySelector('input')!, 'sk-test-abcd1234');
      openai.querySelector('form')!.dispatchEvent(new Event('submit'));
      const req = http.expectOne('/api/me/api-keys/openai');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ values: { apiKey: 'sk-test-abcd1234' } });
      req.flush({
        provider: 'openai',
        saved: true,
        hint: '1234',
        updatedAt: '2026-10-25T12:00:00Z',
      });
      await fixture.whenStable();

      expect(openai.textContent).toContain('ends in 1234');
      expect(openai.querySelector('input')).toBeNull();
      expect(openai.textContent).not.toContain('sk-test-abcd1234');
    });

    it('shows a server field error next to the field', async () => {
      const { fixture, el } = await open([NOT_SAVED('usajobs'), NOT_SAVED('openai')]);
      const usajobs = el.querySelector('#usajobs')!;
      const [key, email] = Array.from(usajobs.querySelectorAll('input'));

      type(key, 'abc');
      type(email, 'me@example.com');
      usajobs.querySelector('form')!.dispatchEvent(new Event('submit'));
      http
        .expectOne('/api/me/api-keys/usajobs')
        .flush(
          { errors: { email: ['Email is not valid.'] } },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();

      expect(usajobs.textContent).toContain('Email is not valid.');
    });

    it('removes a saved key after confirming', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const { fixture, el } = await open([
        NOT_SAVED('usajobs'),
        { provider: 'openai', saved: true, hint: '1234', updatedAt: '2026-10-25T12:00:00Z' },
      ]);
      const openai = el.querySelector('#openai')!;

      Array.from(openai.querySelectorAll('button'))
        .find((b) => b.textContent?.includes('Remove'))!
        .click();
      http.expectOne('/api/me/api-keys/openai').flush(null);
      await fixture.whenStable();

      expect(openai.querySelector('input')).not.toBeNull();
    });
  });
});
