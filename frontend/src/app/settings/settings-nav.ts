import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthState } from '../auth/auth-state';

export type SettingsPage = 'api-keys' | 'digest' | 'email' | 'account' | 'invites';

const PAGES: { page: SettingsPage; label: string; admin?: boolean }[] = [
  { page: 'api-keys', label: 'API keys' },
  { page: 'digest', label: 'Weekly email' },
  { page: 'email', label: 'Sending email' },
  { page: 'account', label: 'Account' },
  { page: 'invites', label: 'Invites', admin: true },
];

/** The links between the settings pages; Invites shows only to admins. */
@Component({
  selector: 'app-settings-nav',
  imports: [RouterLink],
  template: `
    <p class="settings-nav m-0 mb-space-md text-body-sm">
      @for (p of pages(); track p.page; let first = $first) {
        @if (!first) {
          <span class="text-outline" aria-hidden="true"> · </span>
        }
        @if (p.page === current()) {
          <span class="font-semibold" aria-current="page">{{ p.label }}</span>
        } @else {
          <a [routerLink]="['/settings', p.page]">{{ p.label }}</a>
        }
      }
    </p>
  `,
})
export class SettingsNav {
  private readonly auth = inject(AuthState);

  readonly current = input.required<SettingsPage>();

  protected readonly pages = computed(() =>
    PAGES.filter((p) => !p.admin || this.auth.user()?.admin === true),
  );
}
