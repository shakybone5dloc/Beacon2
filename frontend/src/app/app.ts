import { Component, computed, effect, inject } from '@angular/core';
import {
  IsActiveMatchOptions,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { AuthState } from './auth/auth-state';
import { MatchesState } from './searches/matches-state';
import { ThemeChoice, ThemeService } from './theme/theme';

const THEME_ICONS: Record<ThemeChoice, string> = {
  system: 'contrast',
  light: 'light_mode',
  dark: 'dark_mode',
};

const THEME_LABELS: Record<ThemeChoice, string> = {
  system: 'Theme: follows your system. Switch to light',
  light: 'Theme: light. Switch to dark',
  dark: 'Theme: dark. Switch to follow your system',
};

interface NavLink {
  path: string;
  label: string;
}

const LINKS: NavLink[] = [
  { path: '/', label: 'Board' },
  { path: '/jobs', label: 'Find jobs' },
  { path: '/matches', label: 'Matches' },
  { path: '/companies', label: 'Companies' },
  { path: '/people', label: 'People' },
  { path: '/stories', label: 'Stories' },
  { path: '/study', label: 'Study' },
  { path: '/documents', label: 'Documents' },
];

const BOARD_ACTIVE: IsActiveMatchOptions = {
  paths: 'exact',
  queryParams: 'ignored',
  matrixParams: 'ignored',
  fragment: 'ignored',
};
const PREFIX_ACTIVE = { exact: false };

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthState);
  protected readonly theme = inject(ThemeService);
  protected readonly matches = inject(MatchesState);

  constructor() {
    // The Matches badge: counted once on sign-in, then again as matches are saved, dismissed or found.
    effect(() => {
      if (this.auth.user()) this.matches.refresh();
    });
  }

  protected readonly themeIcon = computed(() => THEME_ICONS[this.theme.choice()]);
  /** Says what the button does next, since the icon shows the current choice. */
  protected readonly themeLabel = computed(() => THEME_LABELS[this.theme.choice()]);

  protected readonly links = LINKS;
  /** Board matches only "/" itself, but still counts as active with its filters in the address. */
  protected activeOptions(path: string): IsActiveMatchOptions | { exact: boolean } {
    return path === '/' ? BOARD_ACTIVE : PREFIX_ACTIVE;
  }
  /** On small screens the name and Settings link are hidden, so Settings joins the nav row. */
  protected readonly mobileLinks: NavLink[] = [
    ...LINKS,
    { path: '/settings/api-keys', label: 'Settings' },
  ];

  /** "Pat Lee" -> "PL"; an email address gives its first letter. */
  protected readonly initials = computed(() => {
    const user = this.auth.user();
    if (!user) return '';
    const words = (user.displayName ?? user.email).split(/[\s@.]+/).filter((w) => w.length > 0);
    const letters = user.displayName ? words.slice(0, 2).map((w) => w[0]) : [words[0][0]];
    return letters.join('').toUpperCase();
  });

  protected signOut(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
