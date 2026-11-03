import { effect, inject, Injectable, signal, untracked } from '@angular/core';
import { AuthState } from '../auth/auth-state';
import { PostingQuery, PostingSearchResponse } from './posting.model';

/** What the Find jobs form holds, apart from the sources. */
export interface SearchOptions {
  keywords: string;
  location: string;
  remoteOnly: boolean;
}

interface SavedOptions extends SearchOptions {
  excludedSources: string[];
}

/**
 * Keeps the last search while the user visits a saved application and comes back, and
 * the search options (with the unticked sources) in this browser per user, so the form
 * is filled in on the next visit, after a refresh or after signing in again.
 */
@Injectable({ providedIn: 'root' })
export class JobSearchState {
  private readonly auth = inject(AuthState);
  private userId: string | null = null;

  readonly query = signal<PostingQuery | null>(null);
  readonly results = signal<PostingSearchResponse | null>(null);
  /** Source ids the user has unticked; every other usable source is searched, including new ones. */
  readonly excludedSources = signal<ReadonlySet<string>>(new Set());
  /** What the form last held, searched or not. */
  readonly options = signal<SearchOptions | null>(null);

  constructor() {
    this.switchTo(this.auth.user()?.id ?? null);

    // Someone else signing in starts from their own options, never the last user's results.
    effect(() => {
      const userId = this.auth.user()?.id ?? null;
      untracked(() => {
        if (userId !== this.userId) this.switchTo(userId);
      });
    });

    effect(() => {
      const options = this.options();
      const excluded = this.excludedSources();
      if (this.userId && options) {
        write(storageKey(this.userId), { ...options, excludedSources: [...excluded] });
      }
    });
  }

  private switchTo(userId: string | null): void {
    this.userId = userId;
    this.query.set(null);
    this.results.set(null);
    const saved = userId ? read(storageKey(userId)) : null;
    this.options.set(
      saved
        ? { keywords: saved.keywords, location: saved.location, remoteOnly: saved.remoteOnly }
        : null,
    );
    this.excludedSources.set(new Set(saved?.excludedSources ?? []));
  }
}

function storageKey(userId: string): string {
  return `beacon.jobSearch.${userId}`;
}

function read(key: string): SavedOptions | null {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return null;
    const value = JSON.parse(raw) as Partial<SavedOptions>;
    return {
      keywords: typeof value.keywords === 'string' ? value.keywords : '',
      location: typeof value.location === 'string' ? value.location : '',
      remoteOnly: value.remoteOnly === true,
      excludedSources: Array.isArray(value.excludedSources)
        ? value.excludedSources.filter((id): id is string => typeof id === 'string')
        : [],
    };
  } catch {
    // Unavailable storage (private windows) or a damaged value: start with a blank form.
    return null;
  }
}

function write(key: string, value: SavedOptions): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage can be unavailable; the options still last for this visit.
  }
}
