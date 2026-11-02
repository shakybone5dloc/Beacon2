import {
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { ProblemDetail } from '../applications/application.model';
import { problemText } from '../documents/application-documents';
import { CompaniesApi } from './companies-api';
import {
  boardLabel,
  CAREERS_PLATFORM_LABELS,
  careersPageUrl,
  Company,
  Opening,
} from './company.model';

const FIRST_ROLES = 10;

/**
 * A company's place on the watchlist: paste its careers page to watch it, then see
 * what's open there. Watched companies also feed the Watchlist source in Find jobs.
 */
@Component({
  selector: 'app-careers-board',
  imports: [DatePipe],
  templateUrl: './careers-board.html',
})
export class CareersBoard {
  private readonly api = inject(CompaniesApi);

  readonly company = input.required<Company>();
  /** The company after watching or stopping, with its new version. */
  readonly changed = output<Company>();

  protected readonly url = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly openings = signal<Opening[] | null>(null);
  protected readonly openingsError = signal<string | null>(null);
  /** Big employers list hundreds of roles, so only the newest show until asked. */
  protected readonly showAll = signal(false);
  protected readonly shown = computed(() => {
    const list = this.openings() ?? [];
    return this.showAll() ? list : list.slice(0, FIRST_ROLES);
  });

  protected readonly platformLabels = CAREERS_PLATFORM_LABELS;
  protected readonly careersPageUrl = careersPageUrl;
  protected readonly boardLabel = boardLabel;

  constructor() {
    // Load the roles whenever the company starts being watched, or moves to another board.
    effect(() => {
      const { id, careersBoardId } = this.company();
      untracked(() => (careersBoardId ? this.loadOpenings(id) : this.openings.set(null)));
    });
  }

  protected watch(): void {
    const url = this.url().trim();
    if (!url) {
      this.error.set('Paste the link to their careers page first.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .watch(this.company().id, url)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (company) => {
          this.url.set('');
          this.changed.emit(company);
        },
        error: (err: HttpErrorResponse) => {
          const problem = err.error as ProblemDetail | null;
          this.error.set(
            problem?.errors?.['url']?.[0] ?? problemText(err, 'Could not watch the company.'),
          );
        },
      });
  }

  protected stop(): void {
    this.busy.set(true);
    this.api
      .stopWatching(this.company().id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (company) => this.changed.emit(company),
        error: () => this.error.set('Could not stop watching. Try again.'),
      });
  }

  private loadOpenings(companyId: string): void {
    this.openings.set(null);
    this.openingsError.set(null);
    this.showAll.set(false);
    this.api.openings(companyId).subscribe({
      next: (openings) => this.openings.set(openings),
      error: (err: HttpErrorResponse) =>
        this.openingsError.set(problemText(err, 'Could not read their careers board right now.')),
    });
  }
}
