import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { concatMap, finalize } from 'rxjs';
import { parseProblem, problemText } from '../documents/application-documents';
import { AuthApi } from './auth-api';
import { AuthState } from './auth-state';

/** Where a reset link lands: choose a new password, then sign in with it. */
@Component({
  selector: 'app-reset-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth">
      <h1>Choose a new password</h1>
      @if (!token()) {
        <p class="notice">
          This page needs the link from your email.
          <a routerLink="/forgot-password">Ask for a new one</a>.
        </p>
      } @else {
        <p class="intro">This signs you out on every other device.</p>
        @if (formError(); as message) {
          <p class="notice">
            {{ message }}
            @if (expired()) {
              <a routerLink="/forgot-password">Send a new link</a>
            }
          </p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>
            New password
            <input formControlName="password" type="password" autocomplete="new-password" />
            @if (passwordError(); as msg) {
              <span class="error">{{ msg }}</span>
            } @else {
              <span class="hint">At least 12 characters. A few unrelated words works well.</span>
            }
          </label>
          <button type="submit" [disabled]="submitting()">
            {{ submitting() ? 'Saving...' : 'Save new password' }}
          </button>
        </form>
      }
    </section>
  `,
  styleUrl: './auth-page.css',
})
export class ResetPasswordPage {
  private readonly api = inject(AuthApi);
  private readonly auth = inject(AuthState);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** From the link's query string. */
  readonly token = input<string>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly expired = signal(false);
  private readonly serverPasswordError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(12)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);
    this.expired.set(false);
    this.serverPasswordError.set(null);
    this.api
      .csrf()
      .pipe(
        concatMap(() => this.api.resetPassword(this.token()!, this.form.getRawValue().password)),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: () => {
          // Any session in this browser ended with the reset.
          this.auth.clear();
          this.router.navigate(['/login'], { queryParams: { reset: 1 } });
        },
        error: (err: HttpErrorResponse) => {
          const field = parseProblem(err.error)?.errors?.['password']?.[0];
          if (field) {
            this.serverPasswordError.set(`Password ${field}.`);
            return;
          }
          this.expired.set(err.status === 400);
          this.formError.set(
            problemText(err, "Couldn't save the password. Try again in a moment."),
          );
        },
      });
  }

  protected passwordError(): string | null {
    const server = this.serverPasswordError();
    if (server) return server;
    const control = this.form.controls.password;
    if (!control.touched || !control.errors) return null;
    return control.errors['required'] ? 'Required.' : 'At least 12 characters.';
  }
}
