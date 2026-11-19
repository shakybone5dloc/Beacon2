import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthState } from '../auth/auth-state';

interface SettingsLink {
  path: string;
  label: string;
  icon: string;
}

interface SettingsGroup {
  label: string;
  links: SettingsLink[];
  admin?: boolean;
}

export const SETTINGS_GROUPS: SettingsGroup[] = [
  {
    label: 'Account',
    links: [{ path: '/settings/account', label: 'Account', icon: 'person' }],
  },
  {
    label: 'Notifications',
    links: [{ path: '/settings/digest', label: 'Weekly email', icon: 'mail' }],
  },
  {
    label: 'AI & job sources',
    links: [
      { path: '/settings/api-keys', label: 'API keys', icon: 'key' },
      { path: '/providers', label: 'Getting API keys', icon: 'help' },
    ],
  },
  {
    label: 'Admin',
    admin: true,
    links: [{ path: '/settings/invites', label: 'Invites', icon: 'confirmation_number' }],
  },
];

/**
 * Every settings page sits in this frame: a grouped menu down the left on wide screens, a row of
 * links that scrolls sideways on narrow ones. Admin shows only to admins.
 */
@Component({
  selector: 'app-settings-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="flex flex-col gap-space-lg md:flex-row md:items-start">
      <nav class="settings-nav shrink-0 md:sticky md:top-20 md:w-56" aria-label="Settings">
        <p class="m-0 mb-space-sm text-headline-md text-on-surface md:mb-space-md">Settings</p>
        <div
          class="-mx-gutter flex gap-space-xs overflow-x-auto px-gutter pb-space-xs md:mx-0 md:flex-col md:gap-space-md md:overflow-visible md:px-0 md:pb-0"
        >
          @for (group of groups(); track group.label) {
            <div class="contents md:block">
              <p
                class="m-0 mb-space-xs hidden px-space-sm font-mono text-label-code tracking-wide text-outline uppercase md:block"
              >
                {{ group.label }}
              </p>
              @for (link of group.links; track link.path) {
                <a
                  [routerLink]="link.path"
                  routerLinkActive="active !bg-surface-container !font-semibold !text-on-surface"
                  class="flex shrink-0 items-center gap-space-sm rounded-sm px-space-sm py-1.5 text-body-md whitespace-nowrap text-on-surface-variant no-underline hover:bg-surface-container-low hover:text-on-surface"
                >
                  <span class="material-symbols-outlined !text-[18px]" aria-hidden="true">{{
                    link.icon
                  }}</span>
                  {{ link.label }}
                </a>
              }
            </div>
          }
        </div>
      </nav>
      <div class="min-w-0 flex-1">
        <router-outlet />
      </div>
    </div>
  `,
})
export class SettingsLayout {
  private readonly auth = inject(AuthState);

  protected readonly groups = computed(() =>
    SETTINGS_GROUPS.filter((g) => !g.admin || this.auth.user()?.admin === true),
  );
}
