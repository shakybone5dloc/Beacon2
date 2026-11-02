import { inject, Injectable, signal } from '@angular/core';
import { SearchesApi } from './searches-api';

/** How many matches are waiting, for the badge on the Matches link. */
@Injectable({ providedIn: 'root' })
export class MatchesState {
  private readonly api = inject(SearchesApi);

  readonly newCount = signal(0);

  refresh(): void {
    this.api.newMatchCount().subscribe({
      next: (count) => this.newCount.set(count),
      error: () => this.newCount.set(0),
    });
  }
}
