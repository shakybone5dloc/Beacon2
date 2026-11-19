import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthState } from '../auth/auth-state';
import { parseProblem, problemText } from '../documents/application-documents';
import { SendFromSettings } from './send-from-settings';

/** Settings · Account: the send-from address, download everything, or delete the account and all its data (L3). */
@Component({
  selector: 'app-account-page',
  imports: [SendFromSettings, FormsModule],
  templateUrl: './account-page.html',
  host: { class: 'block max-w-2xl' },
})
export class AccountPage {
  private readonly auth = inject(AuthState);
  private readonly router = inject(Router);

  protected readonly email = () => this.auth.user()?.email ?? '';
  protected readonly password = signal('');
  protected readonly understood = signal(false);
  protected readonly deleting = signal(false);
  protected readonly passwordError = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected deleteAccount(): void {
    if (!this.understood() || !this.password()) return;
    this.deleting.set(true);
    this.passwordError.set(null);
    this.notice.set(null);
    this.auth
      .deleteAccount(this.password())
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/login'], { queryParams: { deleted: 1 } }),
        error: (err: HttpErrorResponse) => {
          // The delete has no body, so its errors arrive as text.
          const field = parseProblem(err.error)?.errors?.['password']?.[0];
          if (err.status === 400 && field) this.passwordError.set(field);
          else this.notice.set(problemText(err, 'Could not delete the account. Try again.'));
        },
      });
  }
}
