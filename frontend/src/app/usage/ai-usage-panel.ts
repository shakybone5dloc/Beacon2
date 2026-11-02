import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { problemText } from '../documents/application-documents';
import { UsageApi } from './usage-api';
import { AiUsage, BUDGET_WARNING, FEATURE_LABELS, monthLabel, tokens, usd } from './usage.model';

/**
 * Settings: this month's AI spending by feature and by model, the months before it,
 * and an optional monthly budget.
 */
@Component({
  selector: 'app-ai-usage-panel',
  imports: [ReactiveFormsModule],
  templateUrl: './ai-usage-panel.html',
})
export class AiUsagePanel implements OnInit {
  private readonly api = inject(UsageApi);

  protected readonly usage = signal<AiUsage | null>(null);
  protected readonly loadError = signal(false);
  protected readonly editingBudget = signal(false);
  protected readonly saving = signal(false);
  protected readonly budgetError = signal<string | null>(null);
  protected readonly budget = new FormControl<number | null>(null);

  protected readonly Math = Math;
  protected readonly usd = usd;
  protected readonly tokens = tokens;
  protected readonly monthLabel = monthLabel;
  protected readonly featureLabel = (name: string) => FEATURE_LABELS[name] ?? name;

  /** Share of the budget used, 0 to 1 and beyond; null without a budget. */
  protected readonly budgetUsed = computed(() => {
    const u = this.usage();
    return u?.budgetUsd ? u.totalUsd / u.budgetUsd : null;
  });
  protected readonly budgetState = computed(() => {
    const used = this.budgetUsed();
    if (used == null) return null;
    return used >= 1 ? 'over' : used >= BUDGET_WARNING ? 'warning' : 'ok';
  });
  /** Rounded down, so 79.6% reads "79%" and the warning starts exactly where the text says 80%. */
  protected readonly budgetPercent = computed(() => {
    const used = this.budgetUsed();
    return used == null ? null : Math.floor(used * 100 + 1e-9);
  });

  /** Bar heights as a share of the most expensive month; a month with calls always shows a sliver. */
  protected readonly bars = computed(() => {
    const months = this.usage()?.recentMonths ?? [];
    const max = Math.max(...months.map((m) => m.costUsd), 0);
    return months.map((m) => ({
      ...m,
      height: max > 0 ? Math.max((m.costUsd / max) * 100, m.costUsd > 0 ? 4 : 0) : 0,
    }));
  });

  ngOnInit(): void {
    this.api.summary().subscribe({
      next: (u) => this.usage.set(u),
      error: () => this.loadError.set(true),
    });
  }

  protected startBudget(): void {
    this.budget.setValue(this.usage()?.budgetUsd ?? null);
    this.budgetError.set(null);
    this.editingBudget.set(true);
  }

  protected saveBudget(value: number | null = this.budget.value): void {
    if (value != null && !(value > 0)) {
      this.budgetError.set('Enter a monthly amount in dollars, like 20.');
      return;
    }
    this.saving.set(true);
    this.budgetError.set(null);
    this.api.saveBudget(value).subscribe({
      next: (u) => {
        this.usage.set(u);
        this.saving.set(false);
        this.editingBudget.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.budgetError.set(problemText(err, 'Could not save the budget. Try again.'));
      },
    });
  }
}
