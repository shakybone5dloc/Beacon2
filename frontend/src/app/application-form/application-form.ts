import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  CreateApplicationRequest,
  ProblemDetail,
  SALARY_PERIODS,
} from '../applications/application.model';

type FieldName =
  | 'company'
  | 'role'
  | 'jobUrl'
  | 'notes'
  | 'applyBy'
  | 'followUpOn'
  | 'location'
  | 'salaryMin'
  | 'salaryMax'
  | 'salaryCurrency'
  | 'salaryText'
  | 'source'
  | 'jobDescription';

/** Messages for pattern failures, by field. */
const PATTERN_MESSAGES: Partial<Record<FieldName, string>> = {
  jobUrl: 'Must start with http:// or https://',
  salaryMin: 'Whole number, no commas.',
  salaryMax: 'Whole number, no commas.',
  salaryCurrency: 'A 3-letter code, like USD.',
};

@Component({
  selector: 'app-application-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './application-form.html',
  styleUrl: './application-form.css',
})
export class ApplicationForm implements OnInit {
  private readonly api = inject(ApplicationsApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Bound from the `:id` route param; absent when creating. */
  readonly id = input<string>();

  protected readonly isEdit = computed(() => this.id() != null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly stale = signal(false);
  /** The posting details section starts open when editing an application that has some. */
  protected readonly detailsOpen = signal(false);
  protected readonly periods = SALARY_PERIODS;

  private version = 0;

  protected readonly form = this.fb.group({
    company: ['', [Validators.required, Validators.maxLength(200)]],
    role: ['', [Validators.required, Validators.maxLength(200)]],
    jobUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^https?:\/\/.+/)]],
    notes: ['', [Validators.maxLength(4000)]],
    applyBy: [''],
    followUpOn: [''],
    location: ['', [Validators.maxLength(500)]],
    /** '' not specified, 'yes' remote, 'no' on-site or hybrid. */
    remote: [''],
    salaryMin: ['', [Validators.pattern(/^\d+$/)]],
    salaryMax: ['', [Validators.pattern(/^\d+$/)]],
    salaryCurrency: ['', [Validators.pattern(/^[A-Za-z]{3}$/)]],
    salaryPeriod: [''],
    salaryText: ['', [Validators.maxLength(100)]],
    source: ['', [Validators.maxLength(100)]],
    postedOn: [''],
    jobDescription: ['', [Validators.maxLength(100_000)]],
  });

  ngOnInit(): void {
    if (this.isEdit()) {
      this.loadExisting();
    }
  }

  protected loadExisting(): void {
    this.loading.set(true);
    this.formError.set(null);
    this.stale.set(false);

    this.api
      .getById(this.id()!)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (app) => this.fill(app),
        error: (err: HttpErrorResponse) =>
          this.loadError.set(
            err.status === 404
              ? 'That application no longer exists.'
              : 'Could not load the application. Is the API running?',
          ),
      });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const request: CreateApplicationRequest = {
      company: v.company,
      role: v.role,
      jobUrl: v.jobUrl.trim() || null,
      notes: v.notes.trim() || null,
      applyBy: v.applyBy || null,
      followUpOn: v.followUpOn || null,
      location: v.location.trim() || null,
      remote: v.remote === '' ? null : v.remote === 'yes',
      salaryMin: v.salaryMin ? Number(v.salaryMin) : null,
      salaryMax: v.salaryMax ? Number(v.salaryMax) : null,
      salaryCurrency: v.salaryCurrency.trim().toUpperCase() || null,
      salaryPeriod: v.salaryPeriod || null,
      salaryText: v.salaryText.trim() || null,
      source: v.source.trim() || null,
      postedOn: v.postedOn || null,
      jobDescription: v.jobDescription.trim() || null,
    };

    this.submitting.set(true);
    this.formError.set(null);

    const id = this.id();
    const save$ = id
      ? this.api.update(id, { ...request, version: this.version })
      : this.api.create(request);

    save$.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (saved) => this.router.navigate(id ? ['/applications', saved.id] : ['/']),
      error: (err: HttpErrorResponse) => this.applyServerErrors(err),
    });
  }

  protected errorFor(name: FieldName): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;

    const e = control.errors;
    if (e['server']) return e['server'];
    if (e['required']) return 'Required.';
    if (e['maxlength']) return `Must be at most ${e['maxlength'].requiredLength} characters`;
    if (e['pattern']) return PATTERN_MESSAGES[name] ?? 'Invalid.';
    return 'Invalid.';
  }

  private fill(app: ApplicationResponse): void {
    this.version = app.version;
    this.form.reset({
      company: app.company,
      role: app.role,
      jobUrl: app.jobUrl ?? '',
      notes: app.notes ?? '',
      applyBy: app.applyBy ?? '',
      followUpOn: app.followUpOn ?? '',
      location: app.location ?? '',
      remote: app.remote == null ? '' : app.remote ? 'yes' : 'no',
      salaryMin: app.salaryMin?.toString() ?? '',
      salaryMax: app.salaryMax?.toString() ?? '',
      salaryCurrency: app.salaryCurrency ?? '',
      salaryPeriod: app.salaryPeriod ?? '',
      salaryText: app.salaryText ?? '',
      source: app.source ?? '',
      postedOn: app.postedOn ?? '',
      jobDescription: app.jobDescription ?? '',
    });
    this.detailsOpen.set(hasPostingDetails(app));
  }

  private applyServerErrors(err: HttpErrorResponse): void {
    const problem = err.error as ProblemDetail | null;

    if (err.status === 400 && problem?.errors) {
      for (const [field, messages] of Object.entries(problem.errors)) {
        const control = this.form.get(field);
        control?.setErrors({ server: messages[0] });
        control?.markAsTouched();
      }
      return;
    }

    if (err.status === 409) {
      // Keep the user's edits on screen; they choose whether to discard them.
      this.stale.set(true);
      this.formError.set('This application was changed somewhere else since you opened it.');
      return;
    }

    if (err.status === 404) {
      this.formError.set('That application no longer exists.');
      return;
    }

    this.formError.set('Could not save the application. Try again.');
  }
}

function hasPostingDetails(app: ApplicationResponse): boolean {
  return [
    app.location,
    app.remote,
    app.salaryMin,
    app.salaryMax,
    app.salaryText,
    app.source,
    app.postedOn,
    app.jobDescription,
  ].some((v) => v != null);
}
