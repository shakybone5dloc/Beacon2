import { Component, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { AuthState } from './auth-state';
import { safeReturnUrl } from './auth.guards';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login-page.html',
  styleUrl: './auth-page.css',
})
export class LoginPage {
  private readonly auth = inject(AuthState);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Where to go after signing in; bound from the query string. */
  readonly returnUrl = input<string>();

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);

    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (err: HttpErrorResponse) =>
          this.formError.set(problemText(err, 'Could not sign in. Try again in a moment.')),
      });
  }

  protected errorFor(name: 'email' | 'password'): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;
    if (control.errors['required']) return 'Required.';
    if (control.errors['email']) return 'Enter an email address.';
    return 'Invalid.';
  }
}
