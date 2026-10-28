import { DestroyRef, effect, inject, Injectable, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';

/** What the user picked: follow the OS, or always light or dark. */
export type ThemeChoice = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'beacon.theme';
const ORDER: ThemeChoice[] = ['system', 'light', 'dark'];

/**
 * Light or dark. The choice is remembered in this browser; the resolved theme goes on
 * <html data-theme>, which styles.css keys the dark colours on. index.html does the same
 * before Angular starts, so the page never flashes white.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly media = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
  private readonly systemDark = signal(this.media?.matches ?? false);
  private readonly current = signal<ThemeChoice>(readChoice(this.document));

  readonly choice = this.current.asReadonly();

  constructor() {
    const onChange = (e: MediaQueryListEvent) => this.systemDark.set(e.matches);
    this.media?.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => this.media?.removeEventListener('change', onChange));

    effect(() => {
      const choice = this.current();
      const dark = choice === 'dark' || (choice === 'system' && this.systemDark());
      this.document.documentElement.dataset['theme'] = dark ? 'dark' : 'light';
    });
  }

  set(choice: ThemeChoice): void {
    this.current.set(choice);
    try {
      if (choice === 'system') this.document.defaultView?.localStorage.removeItem(STORAGE_KEY);
      else this.document.defaultView?.localStorage.setItem(STORAGE_KEY, choice);
    } catch {
      // Storage blocked: the choice still holds until the page is reloaded.
    }
  }

  /** System → Light → Dark → System. */
  next(): void {
    this.set(ORDER[(ORDER.indexOf(this.current()) + 1) % ORDER.length]);
  }
}

function readChoice(document: Document): ThemeChoice {
  try {
    const saved = document.defaultView?.localStorage.getItem(STORAGE_KEY);
    return saved === 'light' || saved === 'dark' ? saved : 'system';
  } catch {
    return 'system';
  }
}
