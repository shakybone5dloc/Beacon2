import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { verdict, Verdict } from '../fit/fit.model';
import { shortDurationBetween } from '../applications/durations';
import { formatLocation, formatSalary } from '../postings/format';
import { MatchesState } from './matches-state';
import { SearchesApi } from './searches-api';
import { JobMatch, MatchStatus } from './search.model';

const TABS: { status: MatchStatus; label: string }[] = [
  { status: 'NEW', label: 'New' },
  { status: 'BELOW_MIN', label: 'Below your minimum' },
  { status: 'DISMISSED', label: 'Dismissed' },
];

/** Postings your saved searches found, best fit first, to save to the board or dismiss. */
@Component({
  selector: 'app-matches-page',
  imports: [RouterLink],
  templateUrl: './matches-page.html',
})
export class MatchesPage implements OnInit {
  private readonly api = inject(SearchesApi);
  private readonly state = inject(MatchesState);

  protected readonly tabs = TABS;
  protected readonly status = signal<MatchStatus>('NEW');
  protected readonly matches = signal<JobMatch[] | null>(null);
  protected readonly loadError = signal(false);
  /** Ids with a save or dismiss under way. */
  protected readonly busy = signal<ReadonlySet<string>>(new Set());
  /** Application ids for matches saved on this visit, by match id. */
  protected readonly savedAs = signal<ReadonlyMap<string, string>>(new Map());
  protected readonly errors = signal<ReadonlyMap<string, string>>(new Map());

  protected readonly formatSalary = formatSalary;
  protected readonly formatLocation = formatLocation;
  protected readonly verdict = verdict;
  protected readonly fitFill: Record<Verdict['pill'], string> = {
    'pill-green': 'bg-success',
    'pill-blue': 'bg-info',
    'pill-amber': 'bg-attention',
    'pill-red': 'bg-error',
  };

  ngOnInit(): void {
    this.load();
  }

  protected show(status: MatchStatus): void {
    if (status === this.status()) return;
    this.status.set(status);
    this.load();
  }

  protected posted(match: JobMatch): string | null {
    return match.postedAt ? `${shortDurationBetween(match.postedAt, new Date())} ago` : null;
  }

  protected save(match: JobMatch): void {
    this.act(match, this.api.saveMatch(match.id), (app) => {
      this.savedAs.update((m) => new Map(m).set(match.id, app.id));
      this.state.refresh();
    });
  }

  protected dismiss(match: JobMatch): void {
    this.act(match, this.api.dismissMatch(match.id), () => {
      this.matches.update((list) => list?.filter((m) => m.id !== match.id) ?? null);
      this.state.refresh();
    });
  }

  protected restore(match: JobMatch): void {
    this.act(match, this.api.restoreMatch(match.id), () => {
      this.matches.update((list) => list?.filter((m) => m.id !== match.id) ?? null);
      this.state.refresh();
    });
  }

  private act<T>(match: JobMatch, request: Observable<T>, done: (result: T) => void): void {
    this.busy.update((s) => new Set(s).add(match.id));
    this.errors.update((m) => {
      const next = new Map(m);
      next.delete(match.id);
      return next;
    });
    request.subscribe({
      next: (result) => {
        this.release(match.id);
        done(result);
      },
      error: (err: HttpErrorResponse) => {
        this.release(match.id);
        this.errors.update((m) =>
          new Map(m).set(match.id, problemText(err, 'That didn’t work. Try again.')),
        );
      },
    });
  }

  private release(id: string): void {
    this.busy.update((s) => {
      const next = new Set(s);
      next.delete(id);
      return next;
    });
  }

  private load(): void {
    this.matches.set(null);
    this.loadError.set(false);
    this.api.matches(this.status()).subscribe({
      next: (list) => this.matches.set(list),
      error: () => this.loadError.set(true),
    });
  }
}
