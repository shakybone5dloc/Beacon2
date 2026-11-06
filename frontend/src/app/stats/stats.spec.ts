import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { StatsPage } from './stats-page';
import { days, funnel, percent, Stats, StatsGroup } from './stats.model';

function aGroup(overrides: Partial<StatsGroup> = {}): StatsGroup {
  return {
    name: null,
    total: 10,
    applied: 8,
    responded: 4,
    interviewed: 2,
    offered: 1,
    ghosted: 3,
    medianDaysToResponse: 4.5,
    ...overrides,
  };
}

function someStats(overrides: Partial<Stats> = {}): Stats {
  return {
    since: null,
    overall: aGroup(),
    bySource: [aGroup({ name: 'LinkedIn', applied: 5 }), aGroup({ name: 'Not recorded' })],
    byResume: [aGroup({ name: 'Backend resume' })],
    stages: [
      { stage: 'SAVED', moved: 8, current: 2, averageDays: 1.5, medianDays: 1 },
      { stage: 'APPLIED', moved: 4, current: 4, averageDays: 6, medianDays: 4.5 },
    ],
    weeks: Array.from({ length: 12 }, (_, i) => ({
      weekStart: `2026-08-${String(17 + i).padStart(2, '0')}`,
      applied: i === 11 ? 4 : 0,
    })),
    ...overrides,
  };
}

it('formats shares and day counts', () => {
  expect(percent(1, 3)).toBe('33%');
  expect(percent(0, 0)).toBe('–');
  expect(days(1)).toBe('1 day');
  expect(days(3.5)).toBe('3.5 days');
  expect(days(null)).toBe('–');
  expect(days(0.4)).toBe('under a day');
  expect(funnel(aGroup()).map((s) => s.share)).toEqual([1, 0.5, 0.25, 0.125]);
  expect(funnel(aGroup({ applied: 0, responded: 0 }))[1].share).toBe(0);
});

describe('StatsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(stats: Stats) {
    const fixture = TestBed.createComponent(StatsPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/stats' && !r.params.has('days')).flush(stats);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('shows the rates, the funnel, the weekly columns and the tables', async () => {
    const { el } = await open(someStats());

    expect(el.querySelector('.response')?.textContent?.trim()).toBe('50%');
    expect(el.querySelector('.ghosted')?.textContent?.trim()).toBe('38%');
    expect(el.textContent).toContain('usually in 4.5 days');

    const bars = Array.from(el.querySelectorAll<HTMLElement>('.funnel .bar'));
    expect(bars.map((b) => b.style.width)).toEqual(['100%', '50%', '25%', '12.5%']);

    const weeks = el.querySelectorAll<HTMLElement>('.week-bar');
    expect(weeks.length).toBe(12);
    expect(weeks[11].style.height).toBe('100%');
    expect(weeks[0].style.height).toBe('0%');

    expect(
      Array.from(el.querySelectorAll('.source-row td:first-child')).map((c) =>
        c.textContent?.trim(),
      ),
    ).toEqual(['LinkedIn', 'Not recorded']);
    expect(el.querySelector('.stage-row')?.textContent).toContain('Saved');
    expect(el.querySelector('.resume-row')?.textContent).toContain('Backend resume');
  });

  it('asks again for the chosen range', async () => {
    const { fixture, el } = await open(someStats());

    const select = el.querySelector<HTMLSelectElement>('select.range')!;
    select.value = '30';
    select.dispatchEvent(new Event('change'));

    const req = http.expectOne((r) => r.url === '/api/stats');
    expect(req.request.params.get('days')).toBe('30');
    req.flush(someStats({ overall: aGroup({ total: 0, applied: 0 }) }));
    await fixture.whenStable();

    expect(el.querySelector('.empty')?.textContent).toContain('No applications in this range');
  });
});
