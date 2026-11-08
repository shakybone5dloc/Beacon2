import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { ApplicationResponse, employerLabel } from '../applications/application.model';
import { problemText } from '../documents/application-documents';
import { AuthState } from '../auth/auth-state';
import { MatchesState } from '../searches/matches-state';
import { JobMatch } from '../searches/search.model';
import { SearchesApi } from '../searches/searches-api';
import { StatsApi } from '../stats/stats-api';
import { percent, Stats } from '../stats/stats.model';
import { StudyApi } from '../study/study-api';
import { Deck } from '../study/study.model';
import { browserZone } from '../usage/usage.model';
import { verdict } from '../fit/fit.model';
import { HomeApi } from './home-api';
import {
  dueDecks,
  greeting,
  HomeTodos,
  needingAttention,
  queue,
  sentThisWeek,
  stageCounts,
  upcomingInterviews,
} from './home.model';

type Source = 'applications' | 'matches' | 'decks' | 'stats' | 'todos';

/**
 * Home (B2): what needs the user right now, gathered from across Beacon. Each section
 * loads on its own and hides when it has nothing to show.
 */
@Component({
  selector: 'app-home-page',
  imports: [DatePipe, RouterLink],
  templateUrl: './home-page.html',
  host: { class: 'block max-w-5xl' },
})
export class HomePage implements OnInit {
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly searchesApi = inject(SearchesApi);
  private readonly studyApi = inject(StudyApi);
  private readonly statsApi = inject(StatsApi);
  private readonly homeApi = inject(HomeApi);
  private readonly auth = inject(AuthState);
  private readonly matchesState = inject(MatchesState);

  protected readonly now = signal(new Date());
  protected readonly applications = signal<ApplicationResponse[] | null>(null);
  protected readonly matches = signal<JobMatch[] | null>(null);
  protected readonly decks = signal<Deck[] | null>(null);
  protected readonly stats = signal<Stats | null>(null);
  protected readonly todos = signal<HomeTodos | null>(null);
  protected readonly failed = signal<ReadonlySet<Source>>(new Set());
  protected readonly busy = signal<ReadonlySet<string>>(new Set());
  protected readonly notice = signal<string | null>(null);

  protected readonly employerLabel = employerLabel;
  protected readonly percent = percent;
  protected readonly verdict = verdict;

  protected readonly greeting = computed(() => greeting(this.now(), this.auth.user()?.displayName));
  protected readonly attention = computed(() =>
    needingAttention(this.applications() ?? [], this.now()),
  );
  protected readonly upcoming = computed(() =>
    upcomingInterviews(this.applications() ?? [], this.decks() ?? [], this.now()),
  );
  protected readonly queue = computed(() => queue(this.matches() ?? []));
  protected readonly dueDecks = computed(() => dueDecks(this.decks() ?? []));
  protected readonly cardsDue = computed(() => this.dueDecks().reduce((n, d) => n + d.due, 0));
  protected readonly week = computed(() => sentThisWeek(this.stats()?.weeks ?? []));
  protected readonly stages = computed(() => stageCounts(this.applications() ?? []));
  protected readonly todoCount = computed(() => {
    const t = this.todos();
    return t ? t.missingSentResume.length + t.openSuggestions.length : 0;
  });

  /** Everything loaded and nothing is waiting on the user. */
  protected readonly caughtUp = computed(
    () =>
      this.applications() != null &&
      this.matches() != null &&
      this.decks() != null &&
      this.todos() != null &&
      this.attention().length === 0 &&
      this.upcoming().length === 0 &&
      this.queue().length === 0 &&
      this.cardsDue() === 0 &&
      this.todoCount() === 0,
  );

  ngOnInit(): void {
    this.load('applications', this.applicationsApi.list(), (v) => this.applications.set(v));
    this.load('matches', this.searchesApi.matches('NEW'), (v) => this.matches.set(v));
    this.load('decks', this.studyApi.decks(), (v) => this.decks.set(v));
    this.load('stats', this.statsApi.get(null, browserZone()), (v) => this.stats.set(v));
    this.load('todos', this.homeApi.todos(), (v) => this.todos.set(v));
  }

  protected completeFollowUp(app: ApplicationResponse): void {
    this.act(
      app.id,
      this.applicationsApi.scheduleFollowUp(app.id, null, app.version),
      (updated) => {
        this.now.set(new Date());
        this.applications.update(
          (list) => list?.map((a) => (a.id === updated.id ? updated : a)) ?? null,
        );
      },
    );
  }

  protected saveMatch(match: JobMatch): void {
    this.act(match.id, this.searchesApi.saveMatch(match.id), (app) => {
      this.matches.update((list) => list?.filter((m) => m.id !== match.id) ?? null);
      this.applications.update((list) => (list ? [app, ...list] : list));
      this.matchesState.refresh();
    });
  }

  protected dismissMatch(match: JobMatch): void {
    this.act(match.id, this.searchesApi.dismissMatch(match.id), () => {
      this.matches.update((list) => list?.filter((m) => m.id !== match.id) ?? null);
      this.matchesState.refresh();
    });
  }

  protected isBusy(id: string): boolean {
    return this.busy().has(id);
  }

  private load<T>(source: Source, request: Observable<T>, done: (value: T) => void): void {
    request.subscribe({
      next: done,
      error: () => this.failed.update((s) => new Set(s).add(source)),
    });
  }

  private act<T>(id: string, request: Observable<T>, done: (result: T) => void): void {
    this.busy.update((s) => new Set(s).add(id));
    this.notice.set(null);
    const release = () =>
      this.busy.update((s) => {
        const next = new Set(s);
        next.delete(id);
        return next;
      });
    request.subscribe({
      next: (result) => {
        release();
        done(result);
      },
      error: (err: HttpErrorResponse) => {
        release();
        this.notice.set(problemText(err, 'That didn’t work. Try again.'));
      },
    });
  }
}
