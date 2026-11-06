import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { browserZone } from '../usage/usage.model';
import { StatsApi } from './stats-api';
import { days, funnel, percent, RANGES, STAGE_LABELS, Stats } from './stats.model';

/**
 * The job search in numbers (A3): how far applications get and where they come from,
 * how long each stage takes, and how many go out each week.
 */
@Component({
  selector: 'app-stats-page',
  imports: [DatePipe, RouterLink],
  templateUrl: './stats-page.html',
  host: { class: 'block max-w-5xl' },
})
export class StatsPage implements OnInit {
  private readonly api = inject(StatsApi);

  protected readonly stats = signal<Stats | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly range = signal<number | null>(null);

  protected readonly ranges = RANGES;
  protected readonly stageLabels = STAGE_LABELS;
  protected readonly percent = percent;
  protected readonly days = days;

  protected readonly funnel = computed(() => {
    const s = this.stats();
    return s ? funnel(s.overall) : [];
  });

  /** The tallest week sets the scale; at least 1 so an empty chart still draws. */
  protected readonly weekMax = computed(() =>
    Math.max(1, ...(this.stats()?.weeks ?? []).map((w) => w.applied)),
  );

  ngOnInit(): void {
    this.load();
  }

  protected setRange(value: string): void {
    this.range.set(value === '' ? null : Number(value));
    this.load();
  }

  private load(): void {
    this.error.set(null);
    this.api.get(this.range(), browserZone()).subscribe({
      next: (stats) => this.stats.set(stats),
      error: () => this.error.set('Could not load the stats. Is the API running?'),
    });
  }
}
