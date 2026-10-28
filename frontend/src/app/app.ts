import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthState } from './auth/auth-state';

interface NavLink {
  path: string;
  label: string;
}

const LINKS: NavLink[] = [
  { path: '/', label: 'Board' },
  { path: '/jobs', label: 'Find jobs' },
  { path: '/companies', label: 'Companies' },
  { path: '/people', label: 'People' },
  { path: '/documents', label: 'Documents' },
];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
})
export class App {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthState);

  protected readonly links = LINKS;
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
