import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Fit, verdict } from './fit.model';

/** One fit result: the score and verdict, why, and what matched, what's missing and what may rule it out. */
@Component({
  selector: 'app-fit-result',
  imports: [DatePipe],
  template: `
    @let f = fit();
    <div class="flex flex-wrap items-center gap-space-sm">
      <span class="score font-mono text-headline-sm font-semibold">{{ f.score }}</span>
      <span class="verdict pill" [class]="v().pill">{{ v().label }}</span>
      <span class="text-body-sm text-on-surface-variant">
        vs {{ f.resumeLabel }} · {{ f.scoredAt | date: 'mediumDate' }}
      </span>
    </div>
    @if (f.dealbreakers.length > 0) {
      <div
        class="dealbreakers mt-space-sm rounded-sm border border-error-border bg-error-bg p-space-sm text-body-sm"
      >
        <span class="font-semibold text-error">Could rule you out:</span>
        {{ f.dealbreakers.join(' · ') }}
      </div>
    }
    <p class="rationale m-0 mt-space-sm text-body-sm">{{ f.rationale }}</p>
    <div class="mt-space-sm grid gap-space-sm sm:grid-cols-2">
      @if (f.matchedSkills.length > 0) {
        <div>
          <span class="text-label-sm font-semibold text-success">Matches</span>
          <ul class="matched m-0 mt-space-xs flex list-none flex-wrap gap-space-xs p-0">
            @for (s of f.matchedSkills; track s) {
              <li class="pill pill-green">{{ s }}</li>
            }
          </ul>
        </div>
      }
      @if (f.gaps.length > 0) {
        <div>
          <span class="text-label-sm font-semibold text-attention">Gaps</span>
          <ul class="gaps m-0 mt-space-xs flex list-none flex-wrap gap-space-xs p-0">
            @for (s of f.gaps; track s) {
              <li class="pill pill-amber">{{ s }}</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
})
export class FitResult {
  readonly fit = input.required<Fit>();

  protected readonly v = computed(() => verdict(this.fit().score));
}
