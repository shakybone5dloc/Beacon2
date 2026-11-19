import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { AuthApi } from './auth-api';
import { AuthState } from './auth-state';

/** Asks a signed-in user to confirm their email, with a way to send the link again. */
@Component({
  selector: 'app-confirm-email-banner',
  template: `
    @if (auth.user()?.emailConfirmationNeeded) {
      <div class="confirm-banner border-b border-attention-border bg-attention-panel" role="status">
        <p
          class="mx-auto m-0 flex max-w-7xl flex-wrap items-center gap-x-space-sm gap-y-space-xs px-gutter py-space-sm text-body-sm"
        >
          <span class="material-symbols-outlined !text-[18px] text-attention" aria-hidden="true"
            >mail</span
          >
          <span>
            Confirm your email: we sent a link to <strong>{{ auth.user()?.email }}</strong
            >.
          </span>
          @if (sent()) {
            <span class="sent text-on-surface-variant">Sent. Check your inbox.</span>
          } @else {
            <button
              type="button"
              class="resend min-h-0 border-0 bg-transparent p-0 font-sans text-body-sm text-primary-container hover:underline"
              [disabled]="sending()"
              (click)="resend()"
            >
              {{ sending() ? 'Sending...' : 'Resend' }}
            </button>
          }
          @if (error(); as message) {
            <span class="text-error">{{ message }}</span>
          }
        </p>
      </div>
    }
  `,
})
export class ConfirmEmailBanner {
  protected readonly auth = inject(AuthState);
  private readonly api = inject(AuthApi);

  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  protected readonly error = signal<string | null>(null);

  protected resend(): void {
    this.sending.set(true);
    this.error.set(null);
    this.api
      .resendConfirmation()
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: () => this.sent.set(true),
        error: (err: HttpErrorResponse) =>
          this.error.set(problemText(err, "Couldn't send it. Try again in a moment.")),
      });
  }
}
