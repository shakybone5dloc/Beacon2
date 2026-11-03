import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { SALARY_PERIODS } from '../applications/application.model';
import { formatLocation, formatPosted, formatSalary } from '../postings/format';
import { JobSearchState } from '../postings/job-search-state';
import { JobPosting, JobSource } from '../postings/posting.model';
import { PostingsApi } from '../postings/postings-api';
import { problemText } from '../documents/application-documents';
import { FitApi } from '../fit/fit-api';
import { Fit, matchingSkills } from '../fit/fit.model';
import { FitResult } from '../fit/fit-result';
import { Resume } from '../resumes/resume.model';
import { ResumesApi } from '../resumes/resumes-api';

const MAX_FIELD = 200;

@Component({
  selector: 'app-job-search',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, FitResult],
  templateUrl: './job-search.html',
  styleUrl: './job-search.css',
})
export class JobSearch implements OnInit {
  private readonly postingsApi = inject(PostingsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly fitApi = inject(FitApi);
  private readonly resumesApi = inject(ResumesApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly state = inject(JobSearchState);

  protected readonly sources = signal<JobSource[]>([]);
  protected readonly searching = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Board application id by job URL, for postings already saved. */
  protected readonly savedByUrl = signal<ReadonlyMap<string, string>>(new Map());
  protected readonly savingUrls = signal<ReadonlySet<string>>(new Set());
  protected readonly saveErrors = signal<ReadonlyMap<string, string>>(new Map());

  /** Fit checks run against the default resume. */
  protected readonly defaultResume = signal<Resume | null>(null);
  protected readonly fitConfigured = signal(false);
  /** By posting key (source + id). */
  protected readonly fits = signal<ReadonlyMap<string, Fit>>(new Map());
  protected readonly checkingFits = signal<ReadonlySet<string>>(new Set());
  protected readonly fitErrors = signal<ReadonlyMap<string, string>>(new Map());

  protected readonly form = this.fb.group({
    keywords: ['', [Validators.required]],
    location: [''],
    remoteOnly: [false],
  });

  protected readonly selectedSources = computed(() =>
    this.sources()
      .filter((s) => !this.state.excludedSources().has(s.id))
      .map((s) => s.id),
  );

  protected readonly failures = computed(
    () => this.state.results()?.sources.filter((s) => s.error != null) ?? [],
  );

  /** The current search, for the saved searches page to start a new one from. */
  protected saveSearchParams(): Record<string, string> {
    const v = this.form.getRawValue();
    const all = this.selectedSources().length === this.sources().length;
    return {
      keywords: v.keywords.trim(),
      ...(v.location.trim() ? { location: v.location.trim() } : {}),
      ...(v.remoteOnly ? { remote: 'true' } : {}),
      ...(all ? {} : { sources: this.selectedSources().join(',') }),
    };
  }

  protected readonly formatSalary = formatSalary;
  protected readonly formatLocation = formatLocation;
  protected readonly formatPosted = (p: JobPosting) => formatPosted(p, new Date());

  ngOnInit(): void {
    const last = this.state.options() ?? this.state.query();
    if (last) {
      this.form.setValue({
        keywords: last.keywords,
        location: last.location,
        remoteOnly: last.remoteOnly,
      });
    }
    this.form.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.state.options.set(this.form.getRawValue()));

    this.postingsApi.sources().subscribe({
      next: (sources) => this.sources.set(sources),
      error: () => this.error.set('Could not load job sources. Is the API running?'),
    });

    this.fitApi.isConfigured().subscribe({
      next: (configured) => this.fitConfigured.set(configured),
      error: () => this.fitConfigured.set(false),
    });
    this.resumesApi.list().subscribe({
      next: (resumes) => this.defaultResume.set(resumes.find((r) => r.isDefault) ?? null),
      error: () => this.defaultResume.set(null),
    });

    this.applicationsApi.list().subscribe((apps) => {
      const saved = new Map<string, string>();
      for (const app of apps) {
        if (app.jobUrl) saved.set(app.jobUrl, app.id);
      }
      this.savedByUrl.set(saved);
    });
  }

  protected toggleSource(id: string): void {
    this.state.excludedSources.update((excluded) => {
      const next = new Set(excluded);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected isSelected(id: string): boolean {
    return !this.state.excludedSources().has(id);
  }

  protected search(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.selectedSources().length === 0) {
      this.error.set('Pick at least one source to search.');
      return;
    }

    const v = this.form.getRawValue();
    const query = {
      keywords: v.keywords.trim(),
      location: v.location.trim(),
      remoteOnly: v.remoteOnly,
      sources: this.selectedSources(),
    };

    this.searching.set(true);
    this.error.set(null);
    this.state.query.set(query);

    this.postingsApi
      .search(query)
      .pipe(finalize(() => this.searching.set(false)))
      .subscribe({
        next: (results) => this.state.results.set(results),
        error: () => this.error.set('The search failed. Try again.'),
      });
  }

  protected save(posting: JobPosting): void {
    this.savingUrls.update((s) => new Set(s).add(posting.url));
    this.saveErrors.update((m) => withoutKey(m, posting.url));

    this.applicationsApi
      .create({
        companyName: posting.company ? truncate(posting.company) : null,
        role: truncate(posting.title),
        jobUrl: posting.url.length <= 2048 ? posting.url : null,
        notes: null,
        applyBy: posting.closesOn,
        followUpOn: null,
        location: posting.location ? posting.location.slice(0, 500) : null,
        remote: posting.remote,
        salaryMin: posting.salaryMin,
        salaryMax: posting.salaryMax,
        // Boards occasionally send values the board can't store ("weekly", "US$"); drop those rather than fail the save.
        salaryCurrency: /^[A-Za-z]{3}$/.test(posting.salaryCurrency ?? '')
          ? posting.salaryCurrency
          : null,
        salaryPeriod: (SALARY_PERIODS as readonly string[]).includes(posting.salaryPeriod ?? '')
          ? posting.salaryPeriod
          : null,
        salaryText: posting.salaryText ? posting.salaryText.slice(0, 100) : null,
        source: this.sourceName(posting.source),
        postedOn: posting.postedAt ? posting.postedAt.slice(0, 10) : null,
        // Most boards send the full ad, so the description is there without scraping the page.
        jobDescription: posting.description,
      })
      .pipe(
        finalize(() =>
          this.savingUrls.update((s) => {
            const next = new Set(s);
            next.delete(posting.url);
            return next;
          }),
        ),
      )
      .subscribe({
        next: (app) => {
          this.savedByUrl.update((m) => new Map(m).set(posting.url, app.id));
          // Give the new application the fit already checked; same inputs, so no new AI call.
          const fit = this.fits().get(key(posting));
          if (fit && posting.description) {
            this.fitApi
              .scoreApplication(app.id, fit.resumeDocumentId)
              .subscribe({ error: () => {} });
          }
        },
        error: (err: HttpErrorResponse) =>
          this.saveErrors.update((m) =>
            new Map(m).set(
              posting.url,
              err.status === 400
                ? 'This posting has details the board cannot store.'
                : 'Could not save. Try again.',
            ),
          ),
      });
  }

  /** The default resume's skills this posting mentions; free, before any AI check. */
  protected skillMatches(posting: JobPosting): string[] {
    const skills = this.defaultResume()?.skills ?? [];
    if (skills.length === 0) return [];
    const text = [posting.title, posting.snippet, posting.description, ...posting.tags].join(' ');
    return matchingSkills(skills, text);
  }

  protected canCheckFit(posting: JobPosting): boolean {
    const resume = this.defaultResume();
    return (
      this.fitConfigured() &&
      resume != null &&
      resume.textStatus === 'READ' &&
      !!(posting.description || posting.snippet)
    );
  }

  protected checkFit(posting: JobPosting): void {
    const resume = this.defaultResume();
    const text = (posting.description || posting.snippet || '').trim();
    if (!resume || !text) return;
    const k = key(posting);
    this.checkingFits.update((s) => new Set(s).add(k));
    this.fitErrors.update((m) => withoutKey(m, k));

    this.fitApi
      .scorePosting({
        resumeDocumentId: resume.documentId,
        title: truncate(posting.title),
        company: posting.company ? truncate(posting.company) : null,
        description: text.slice(0, 60_000),
      })
      .pipe(
        finalize(() =>
          this.checkingFits.update((s) => {
            const next = new Set(s);
            next.delete(k);
            return next;
          }),
        ),
      )
      .subscribe({
        next: (fit) => this.fits.update((m) => new Map(m).set(k, fit)),
        error: (err: HttpErrorResponse) =>
          this.fitErrors.update((m) =>
            new Map(m).set(k, problemText(err, 'Could not check the fit. Try again.')),
          ),
      });
  }

  protected readonly key = key;

  protected source(id: string): JobSource | undefined {
    return this.sources().find((s) => s.id === id);
  }

  protected sourceName(id: string): string {
    return this.source(id)?.name ?? id;
  }
}

function key(posting: JobPosting): string {
  return posting.source + '|' + posting.id;
}

function truncate(text: string): string {
  return text.length <= MAX_FIELD ? text : text.slice(0, MAX_FIELD - 1) + '…';
}

function withoutKey<K, V>(map: ReadonlyMap<K, V>, key: K): ReadonlyMap<K, V> {
  const next = new Map(map);
  next.delete(key);
  return next;
}
