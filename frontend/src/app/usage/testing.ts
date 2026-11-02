import { AiUsage } from './usage.model';

/** A month with nothing spent yet; override what a test needs. */
export function anUsage(overrides: Partial<AiUsage> = {}): AiUsage {
  return {
    month: '2026-11',
    totalUsd: 0,
    calls: 0,
    unpricedCalls: 0,
    budgetUsd: null,
    byFeature: [],
    byModel: [],
    recentMonths: ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'].map(
      (month) => ({
        month,
        calls: 0,
        costUsd: 0,
      }),
    ),
    ...overrides,
  };
}
