import { Component, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ProblemDetail } from '../applications/application.model';
import { problemText } from '../documents/application-documents';
import { ProvidersApi } from './providers-api';
import { accessLabel, ApiKeyStatus, Provider } from './provider.model';

/** Add, replace or remove one provider's key. */
@Component({
  selector: 'app-api-key-editor',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './api-key-editor.html',
  styleUrl: './api-key-editor.css',
})
export class ApiKeyEditor {
  private readonly api = inject(ProvidersApi);

  readonly provider = input.required<Provider>();
  readonly status = input.required<ApiKeyStatus>();
  readonly changed = output<ApiKeyStatus>();

  /** Open by default only when nothing is saved yet. */
  protected readonly replacing = signal(false);
  protected readonly editing = computed(() => !this.status().saved || this.replacing());
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Secret fields the user chose to show as plain text. */
  protected readonly revealed = signal<ReadonlySet<string>>(new Set());

  protected readonly accessLabel = accessLabel;

  protected readonly form = computed(
    () =>
      new FormGroup(
        Object.fromEntries(
          this.provider().fields.map((f) => [
            f.name,
            new FormControl('', {
              nonNullable: true,
              validators: f.required
                ? [Validators.required, Validators.maxLength(500)]
                : [Validators.maxLength(500)],
            }),
          ]),
        ),
      ),
  );

  protected toggleReveal(name: string): void {
    const next = new Set(this.revealed());
    if (next.has(name)) next.delete(name);
    else next.add(name);
    this.revealed.set(next);
  }

  protected startReplace(): void {
    this.form().reset();
    this.error.set(null);
    this.replacing.set(true);
  }

  protected cancelReplace(): void {
    this.form().reset();
    this.error.set(null);
    this.replacing.set(false);
  }

  protected save(): void {
    const form = this.form();
    if (form.invalid) {
      form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set(null);

    this.api
      .saveKey(this.provider().id, form.getRawValue() as Record<string, string>)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (status) => {
          form.reset();
          this.revealed.set(new Set());
          this.replacing.set(false);
          this.changed.emit(status);
        },
        error: (err: HttpErrorResponse) => this.applyServerErrors(err),
      });
  }

  protected remove(): void {
    if (!confirm(`Remove your ${this.provider().name} key?`)) return;
    this.busy.set(true);
    this.error.set(null);

    this.api
      .removeKey(this.provider().id)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: () =>
          this.changed.emit({
            provider: this.provider().id,
            saved: false,
            hint: null,
            updatedAt: null,
          }),
        error: (err: HttpErrorResponse) =>
          this.error.set(problemText(err, 'Could not remove the key.')),
      });
  }

  protected errorFor(name: string): string | null {
    const control = this.form().get(name);
    if (!control?.touched || !control.errors) return null;
    if (control.errors['server']) return control.errors['server'];
    if (control.errors['required']) return 'Required.';
    if (control.errors['maxlength']) return 'Too long.';
    return 'Invalid.';
  }

  private applyServerErrors(err: HttpErrorResponse): void {
    const problem = err.error as ProblemDetail | null;
    if (err.status === 400 && problem?.errors) {
      for (const [field, messages] of Object.entries(problem.errors)) {
        const control = this.form().get(field);
        if (control) {
          control.setErrors({ server: messages[0] });
          control.markAsTouched();
        } else {
          this.error.set(messages[0]);
        }
      }
      return;
    }
    this.error.set(problemText(err, 'Could not save the key.'));
  }
}
