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
} from '../applications/application.model';

type FieldName = 'company' | 'role' | 'jobUrl' | 'notes' | 'applyBy' | 'followUpOn';

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

  private version = 0;

  protected readonly form = this.fb.group({
    company: ['', [Validators.required, Validators.maxLength(200)]],
    role: ['', [Validators.required, Validators.maxLength(200)]],
    jobUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^https?:\/\/.+/)]],
    notes: ['', [Validators.maxLength(4000)]],
    applyBy: [''],
    followUpOn: [''],
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
    if (e['pattern']) return 'Must start with http:// or https://';
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
    });
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
