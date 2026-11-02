import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiUsagePanel } from './ai-usage-panel';
import { anUsage } from './testing';
import { AiUsage, usd } from './usage.model';

describe('AiUsagePanel', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(usage: AiUsage) {
    const fixture = TestBed.createComponent(AiUsagePanel);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === '/api/me/ai-usage');
    expect(req.request.params.get('zone')).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    req.flush(usage);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const SPENT = anUsage({
    totalUsd: 4.2,
    calls: 31,
    byFeature: [
      { name: 'FIT_SCORE', calls: 25, inputTokens: 250_000, outputTokens: 40_000, costUsd: 3.1 },
      { name: 'COVER_LETTER', calls: 6, inputTokens: 30_000, outputTokens: 12_000, costUsd: 1.1 },
    ],
    byModel: [
      { name: 'gpt-6-sol', calls: 31, inputTokens: 280_000, outputTokens: 52_000, costUsd: 4.2 },
    ],
  });

  it("shows this month's spending by feature and by model", async () => {
    const { el } = await open(SPENT);

    expect(el.querySelector('h2')?.textContent).toContain('November 2026');
    expect(el.querySelector('.total')?.textContent?.trim()).toBe('$4.20');
    const features = Array.from(el.querySelectorAll('.by-feature tr')).map((tr) =>
      Array.from(tr.querySelectorAll('th > span, td')).map((cell) =>
        cell.textContent!.replace(/\s+/g, ' ').trim(),
      ),
    );
    expect(features).toEqual([
      ['Fit checks', '25 · 250K in · 40K out', '$3.10'],
      ['Cover letters', '6 · 30K in · 12K out', '$1.10'],
    ]);
    expect(el.querySelector('.by-model')?.textContent).toContain('gpt-6-sol');
    expect(el.querySelector('.empty')).toBeNull();
    expect(el.querySelector('[role=meter]')).toBeNull();
  });

  it('says when nothing has been spent yet', async () => {
    const { el } = await open(anUsage());

    expect(el.querySelector('.total')?.textContent?.trim()).toBe('$0.00');
    expect(el.querySelector('.empty')?.textContent).toContain('No AI calls yet');
  });

  it('warns from 80% of the budget and says when it is used up', async () => {
    const { fixture, el } = await open({ ...SPENT, budgetUsd: 5 });

    expect(el.querySelector('[role=meter]')?.getAttribute('aria-valuenow')).toBe('84');
    expect(el.querySelector('.budget-note')?.textContent).toContain(
      '84% of your monthly budget used',
    );
    expect(el.querySelector('.meter-fill')?.className).toContain('bg-attention');

    el.querySelector<HTMLButtonElement>('.set-budget')!.click();
    await fixture.whenStable();
    const input = el.querySelector<HTMLInputElement>('.budget-form input')!;
    input.value = '4';
    input.dispatchEvent(new Event('input'));
    el.querySelector('.budget-form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne((r) => r.url === '/api/me/ai-usage/budget');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ monthlyUsd: 4 });
    req.flush({ ...SPENT, budgetUsd: 4 });
    await fixture.whenStable();

    expect(el.querySelector('.budget-note')?.textContent).toContain(
      'Over budget: $0.20 past your $4.00',
    );
    expect(el.querySelector('.meter-fill')?.className).toContain('bg-error');
    expect(el.querySelector('.budget-form')).toBeNull();
  });

  it('notes calls on models with no price', async () => {
    const { el } = await open({ ...SPENT, unpricedCalls: 2 });

    expect(el.querySelector('.unpriced')?.textContent).toContain(
      '2 calls on a model Beacon has no price for',
    );
  });

  it('shows a fraction of a cent as less than a cent', () => {
    expect(usd(0.0066)).toBe('$0.01');
    expect(usd(0.0012)).toBe('<$0.01');
    expect(usd(0)).toBe('$0.00');
  });
});
