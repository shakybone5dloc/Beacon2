import { Injectable, signal } from '@angular/core';
import { PostingQuery, PostingSearchResponse } from './posting.model';

/** Keeps the last search while the user visits a saved application and comes back. */
@Injectable({ providedIn: 'root' })
export class JobSearchState {
  readonly query = signal<PostingQuery | null>(null);
  readonly results = signal<PostingSearchResponse | null>(null);
  /** Source ids the user has unticked; every other usable source is searched. */
  readonly excludedSources = signal<ReadonlySet<string>>(new Set());
}
