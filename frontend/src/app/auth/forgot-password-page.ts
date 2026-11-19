import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { concatMap, finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { AuthApi } from './auth-api';

/**
 * Asks for a password-reset link. Says the same thing whether or not the address has an
 * account, as the server does, so the page can't be used to find out who's signed up.
 */
@Component({
  selector: 'app-forgot-password-page',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth">
      <h1>Reset your password</h1>
      @if (sentTo(); as email) {
        <p class="sent">
          If <strong>{{ email }}</strong> has a Beacon account, a link to choose a new password is
          on its way. It works once, within an hour. Check your spam folder if it doesn't arrive in
          a few minutes.
        </p>
        <p class="switch"><a routerLink="/login">Back to sign in</a></p>
      } @else {
        <p class="intro">
          Enter your account's email and we'll send you a link to choose a new one.
        </p>
        @if (formError(); as message) {
          <p class="notice">{{ message }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>
            Email
            <input formControlName="email" type="email" autocomplete="username" />
            @if (emailError(); as msg) {
              <span class="error">{{ msg }}</span>
            }
          </label>
          <button type="submit" [disabled]="submitting()">
            {{ submitting() ? 'Sending...' : 'Send the link' }}
          </button>
        </form>
        <p class="switch">Remembered it? <a routerLink="/login">Sign in</a></p>
      }
    </section>
  `,
  styleUrl: './auth-page.css',
})
export class ForgotPasswordPage {
  private readonly api = inject(AuthApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly sentTo = signal<string | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const email = this.form.getRawValue().email.trim();
    this.submitting.set(true);
    this.formError.set(null);
    this.api
      .csrf()
      .pipe(
        concatMap(() => this.api.requestPasswordReset(email)),
        finalize(() => this.submitting.set(false)),
      )
      .subscribe({
        next: () => this.sentTo.set(email),
        error: (err: HttpErrorResponse) =>
          this.formError.set(problemText(err, "Couldn't send the link. Try again in a moment.")),
      });
  }

  protected emailError(): string | null {
    const control = this.form.controls.email;
    if (!control.touched || !control.errors) return null;
    return control.errors['required'] ? 'Required.' : 'Enter an email address.';
  }
}
