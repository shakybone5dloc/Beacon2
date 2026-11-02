import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { problemText } from '../documents/application-documents';
import { JobSource } from '../postings/posting.model';
import { PostingsApi } from '../postings/postings-api';
import { Resume, splitList } from '../resumes/resume.model';
import { ResumesApi } from '../resumes/resumes-api';
import { MatchesState } from './matches-state';
import { SearchesApi } from './searches-api';
import {
  browserZone,
  DAYS,
  DayOfWeek,
  hourLabel,
  nameFromKeywords,
  SavedSearch,
  SaveSearchRequest,
  scheduleLabel,
  WEEKDAYS,
} from './search.model';

/** How often to check on a search that's running. */
const POLL_MS = 3000;

/**
 * Saved searches: what to look for, which days and hour to look, and which resume to
 * fit-score new matches against. A search can also be run now.
 */
@Component({
  selector: 'app-saved-searches-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe],
  templateUrl: './saved-searches-page.html',
})
export class SavedSearchesPage implements OnInit {
  private readonly api = inject(SearchesApi);
  private readonly postingsApi = inject(PostingsApi);
  private readonly resumesApi = inject(ResumesApi);
  private readonly matches = inject(MatchesState);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly searches = signal<SavedSearch[] | null>(null);
  protected readonly loadError = signal(false);
  protected readonly sources = signal<JobSource[]>([]);
  protected readonly resumes = signal<Resume[]>([]);

  /** The search being edited, or 'new'; null while the form is closed. */
  protected readonly editing = signal<SavedSearch | 'new' | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly runErrors = signal<ReadonlyMap<string, string>>(new Map());
  protected readonly days = DAYS;
  protected readonly hours = Array.from({ length: 24 }, (_, h) => h);
  protected readonly hourLabel = hourLabel;
  protected readonly scheduleLabel = scheduleLabel;
  protected readonly zone = browserZone();

  protected readonly form = this.fb.group({
    name: [''],
    keywords: [''],
    excludeWords: [''],
    location: [''],
    remoteOnly: [false],
    salaryMin: new FormControl<number | null>(null),
    postedWithinDays: [7],
    resumeDocumentId: [''],
    minScore: new FormControl<number | null>(null),
    active: [true],
    runHour: [7],
  });
  protected readonly runDays = signal<ReadonlySet<DayOfWeek>>(new Set(WEEKDAYS));
  /** Source ids ticked; none ticked means every source. */
  protected readonly pickedSources = signal<ReadonlySet<string>>(new Set());

  private poll: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.stopPolling());
    this.load();
    this.postingsApi.sources().subscribe({ next: (s) => this.sources.set(s), error: () => {} });
    this.resumesApi.list().subscribe({
      next: (resumes) => {
        this.resumes.set(resumes);
        // A search started from Find jobs opened before the resumes arrived.
        const fallback = resumes.find((r) => r.isDefault)?.documentId;
        if (this.editing() === 'new' && fallback && !this.form.controls.resumeDocumentId.value) {
          this.form.controls.resumeDocumentId.setValue(fallback);
        }
      },
      error: () => {},
    });

    // Find jobs links here with its current search to save it.
    const q = this.route.snapshot.queryParamMap;
    if (q.get('keywords')) {
      this.startNew();
      this.form.patchValue({
        keywords: q.get('keywords') ?? '',
        name: nameFromKeywords(q.get('keywords') ?? ''),
        location: q.get('location') ?? '',
        remoteOnly: q.get('remote') === 'true',
      });
      const sources = q.get('sources');
      if (sources) this.pickedSources.set(new Set(sources.split(',').filter((s) => s)));
    }
  }

  protected startNew(): void {
    const defaultResume = this.resumes().find((r) => r.isDefault);
    this.form.reset({
      name: '',
      keywords: '',
      excludeWords: '',
      location: '',
      remoteOnly: false,
      salaryMin: null,
      postedWithinDays: 7,
      resumeDocumentId: defaultResume?.documentId ?? '',
      minScore: null,
      active: true,
      runHour: 7,
    });
    this.runDays.set(new Set(WEEKDAYS));
    this.pickedSources.set(new Set());
    this.formError.set(null);
    this.editing.set('new');
  }

  protected startEdit(search: SavedSearch): void {
    this.form.reset({
      name: search.name,
      keywords: search.keywords,
      excludeWords: search.excludeWords.join(', '),
      location: search.location ?? '',
      remoteOnly: search.remoteOnly,
      salaryMin: search.salaryMin,
      postedWithinDays: search.postedWithinDays,
      resumeDocumentId: search.resumeDocumentId ?? '',
      minScore: search.minScore,
      active: search.active,
      runHour: search.runHour,
    });
    this.runDays.set(new Set(search.runDays));
    this.pickedSources.set(new Set(search.sources));
    this.formError.set(null);
    this.editing.set(search);
  }

  protected toggleDay(day: DayOfWeek): void {
    this.runDays.update((set) => {
      const next = new Set(set);
      if (next.has(day)) next.delete(day);
      else next.add(day);
      return next;
    });
  }

  protected toggleSource(id: string): void {
    this.pickedSources.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected save(): void {
    const v = this.form.getRawValue();
    if (!v.keywords.trim()) return this.formError.set('Enter keywords to search for.');
    if (this.runDays().size === 0) return this.formError.set('Pick at least one day to run on.');
    const editing = this.editing();
    const body: SaveSearchRequest = {
      name: v.name.trim() || nameFromKeywords(v.keywords),
      keywords: v.keywords.trim(),
      excludeWords: splitList(v.excludeWords),
      location: v.location.trim() || null,
      remoteOnly: v.remoteOnly,
      salaryMin: v.salaryMin || null,
      sources: [...this.pickedSources()],
      postedWithinDays: v.postedWithinDays,
      resumeDocumentId: v.resumeDocumentId || null,
      minScore: v.resumeDocumentId && v.minScore != null ? v.minScore : null,
      active: v.active,
      runDays: DAYS.map((d) => d.day).filter((d) => this.runDays().has(d)),
      runHour: v.runHour,
      timeZone: this.zone,
      version: editing && editing !== 'new' ? editing.version : null,
    };
    this.saving.set(true);
    this.formError.set(null);
    const request =
      editing && editing !== 'new' ? this.api.update(editing.id, body) : this.api.create(body);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(null);
        this.replace(saved, editing === 'new');
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(
          err.status === 409
            ? 'This search changed since you opened it. Close the form and try again.'
            : problemText(err, 'Could not save the search. Try again.'),
        );
      },
    });
  }

  protected run(search: SavedSearch): void {
    this.runErrors.update((m) => {
      const next = new Map(m);
      next.delete(search.id);
      return next;
    });
    this.api.run(search.id).subscribe({
      next: (s) => {
        this.replace(s, false);
        this.schedulePoll();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.schedulePoll();
          return;
        }
        this.runErrors.update((m) =>
          new Map(m).set(search.id, problemText(err, 'Could not start the run. Try again.')),
        );
      },
    });
  }

  protected remove(search: SavedSearch): void {
    if (!confirm(`Delete "${search.name}"? Its matches stay on the Matches page.`)) return;
    this.api.delete(search.id).subscribe({
      next: () => this.searches.update((list) => list?.filter((s) => s.id !== search.id) ?? null),
    });
  }

  protected resumeLabel(id: string | null): string | null {
    if (!id) return null;
    const resume = this.resumes().find((r) => r.documentId === id);
    return resume?.label ?? 'a deleted resume';
  }

  protected sourceNames(ids: readonly string[]): string {
    if (ids.length === 0) return 'All sources';
    return ids.map((id) => this.sources().find((s) => s.id === id)?.name ?? id).join(', ');
  }

  private replace(search: SavedSearch, added: boolean): void {
    this.searches.update((list) =>
      added
        ? [...(list ?? []), search]
        : (list ?? []).map((s) => (s.id === search.id ? search : s)),
    );
  }

  /** While any search is running, reload the list every few seconds. */
  private schedulePoll(): void {
    this.stopPolling();
    this.poll = setTimeout(() => {
      this.poll = null;
      this.api.list().subscribe({
        next: (list) => {
          this.searches.set(list);
          if (list.some((s) => s.running)) this.schedulePoll();
          else this.matches.refresh();
        },
      });
    }, POLL_MS);
  }

  private stopPolling(): void {
    if (this.poll) clearTimeout(this.poll);
    this.poll = null;
  }

  private load(): void {
    this.api.list().subscribe({
      next: (list) => {
        this.searches.set(list);
        if (list.some((s) => s.running)) this.schedulePoll();
      },
      error: () => this.loadError.set(true),
    });
  }
}
