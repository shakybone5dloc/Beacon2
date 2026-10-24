import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { CreateApplicationRequest, ProblemDetail } from '../applications/application.model';

type FieldName = 'company' | 'role' | 'jobUrl' | 'notes';

@Component({
  selector: 'app-new-application',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './new-application.html',
  styleUrl: './new-application.css',
})
export class NewApplication {
  private readonly api = inject(ApplicationsApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    company: ['', [Validators.required, Validators.maxLength(200)]],
    role: ['', [Validators.required, Validators.maxLength(200)]],
    jobUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^https?:\/\/.+/)]],
    notes: ['', [Validators.maxLength(4000)]],
  });

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
    };

    this.submitting.set(true);
    this.formError.set(null);

    this.api
      .create(request)
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/']),
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

    this.formError.set('Could not save the application. Try again.');
  }
}
