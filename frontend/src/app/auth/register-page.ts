import { Component, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ProblemDetail } from '../applications/application.model';
import { problemText } from '../documents/application-documents';
import { AuthApi } from './auth-api';
import { AuthState } from './auth-state';
import { safeReturnUrl } from './auth.guards';

/** Matches the server's rule in RegisterRequest. */
export const MIN_PASSWORD = 12;

type FieldName = 'email' | 'password' | 'displayName' | 'inviteCode';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register-page.html',
  styleUrl: './auth-page.css',
})
export class RegisterPage implements OnInit {
  private readonly auth = inject(AuthState);
  private readonly authApi = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly returnUrl = input<string>();
  /** From a shared sign-up link: ?invite=K7QX-M2PA-9DTR. */
  readonly invite = input<string>();

  /** Whether signing up needs a code; assumed until the server says otherwise. */
  protected readonly inviteOnly = signal(true);

  protected readonly minPassword = MIN_PASSWORD;
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    inviteCode: ['', [Validators.maxLength(40)]],
    displayName: ['', [Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(320)]],
    password: [
      '',
      [Validators.required, Validators.minLength(MIN_PASSWORD), Validators.maxLength(200)],
    ],
  });

  ngOnInit(): void {
    const code = this.invite();
    if (code) this.form.controls.inviteCode.setValue(code);
    this.authApi.registration().subscribe({
      next: (r) => this.inviteOnly.set(r.inviteOnly),
      // Ask for the code anyway: the server says so if it isn't needed.
      error: () => this.inviteOnly.set(true),
    });
  }

  protected submit(): void {
    if (this.inviteOnly() && !this.form.controls.inviteCode.value.trim()) {
      this.form.controls.inviteCode.setErrors({ required: true });
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.formError.set(null);

    const value = this.form.getRawValue();
    this.auth
      .register({
        ...value,
        displayName: value.displayName.trim() || null,
        inviteCode: this.inviteOnly() ? value.inviteCode.trim() : null,
      })
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
        error: (err: HttpErrorResponse) => this.applyServerErrors(err),
      });
  }

  protected errorFor(name: FieldName): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;

    const e = control.errors;
    if (e['server']) return e['server'];
    if (e['required']) return 'Required.';
    if (e['email']) return 'Enter an email address.';
    if (e['minlength']) return `At least ${e['minlength'].requiredLength} characters.`;
    if (e['maxlength']) return `Must be at most ${e['maxlength'].requiredLength} characters.`;
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
    this.formError.set(problemText(err, 'Could not create the account. Try again in a moment.'));
  }
}
