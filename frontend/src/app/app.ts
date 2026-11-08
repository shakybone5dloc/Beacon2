import { Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import {
  IsActiveMatchOptions,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';
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

export interface NavLink {
  path: string;
  label: string;
}

/** A top-level nav entry: a page, or a menu of related pages. */
export type NavItem = (NavLink & { links?: never }) | { label: string; links: NavLink[] };

/** Six entries, related pages grouped under a menu so the header stays uncrowded. */
export const NAV: NavItem[] = [
  { path: '/', label: 'Home' },
  { path: '/board', label: 'Board' },
  {
    label: 'Jobs',
    links: [
      { path: '/jobs', label: 'Find jobs' },
      { path: '/matches', label: 'Matches' },
      { path: '/searches', label: 'Saved searches' },
    ],
  },
  {
    label: 'Network',
    links: [
      { path: '/companies', label: 'Companies' },
      { path: '/people', label: 'People' },
    ],
  },
  {
    label: 'Prep',
    links: [
      { path: '/stories', label: 'Stories' },
      { path: '/study', label: 'Study' },
      { path: '/documents', label: 'Documents' },
    ],
  },
  { path: '/stats', label: 'Stats' },
];

/** True when the address is one of the menu's pages or below it, filters aside. */
export function menuIsActive(links: readonly NavLink[], url: string): boolean {
  const path = url.split(/[?#]/)[0];
  return links.some((link) => path === link.path || path.startsWith(link.path + '/'));
}

const HOME_ACTIVE: IsActiveMatchOptions = {
  paths: 'exact',
  queryParams: 'ignored',
  matrixParams: 'ignored',
  fragment: 'ignored',
};
const PREFIX_ACTIVE = { exact: false };

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CdkMenu, CdkMenuItem, CdkMenuTrigger],
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

  protected readonly nav = NAV;
  /** Home matches only "/" itself; the others also count as active on their sub-pages and with filters. */
  protected activeOptions(path: string): IsActiveMatchOptions | { exact: boolean } {
    return path === '/' ? HOME_ACTIVE : PREFIX_ACTIVE;
  }

  /** The current address, so a menu's button can show you're on one of its pages. */
  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected menuActive(links: readonly NavLink[]): boolean {
    return menuIsActive(links, this.url());
  }

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
