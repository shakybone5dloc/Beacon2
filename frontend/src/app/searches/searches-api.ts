import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  createSavedSearch,
  deleteSavedSearch,
  dismissMatch,
  getMatchSummary,
  getSavedSearch,
  listMatches,
  listSavedSearches,
  restoreMatch,
  runSavedSearch,
  saveMatch,
  updateSavedSearch,
} from '../api/functions';
import { ApplicationResponse } from '../applications/application.model';
import { JobMatch, MatchStatus, SavedSearch, SaveSearchRequest } from './search.model';

/** Saved searches, and the matches their runs find. */
@Injectable({ providedIn: 'root' })
export class SearchesApi {
  private readonly api = inject(BeaconApi);

  list(): Observable<SavedSearch[]> {
    return this.api.invoke(listSavedSearches);
  }

  get(id: string): Observable<SavedSearch> {
    return this.api.invoke(getSavedSearch, { id });
  }

  create(body: SaveSearchRequest): Observable<SavedSearch> {
    return this.api.invoke(createSavedSearch, { body });
  }

  update(id: string, body: SaveSearchRequest): Observable<SavedSearch> {
    return this.api.invoke(updateSavedSearch, { id, body });
  }

  delete(id: string): Observable<void> {
    return this.api.invoke(deleteSavedSearch, { id });
  }

  /** Starts a run; poll {@link get} until it's no longer running. */
  run(id: string): Observable<SavedSearch> {
    return this.api.invoke(runSavedSearch, { id });
  }

  matches(status: MatchStatus = 'NEW'): Observable<JobMatch[]> {
    return this.api.invoke(listMatches, { status });
  }

  newMatchCount(): Observable<number> {
    return this.api.invoke(getMatchSummary).pipe(map((s) => s.newCount));
  }

  saveMatch(id: string): Observable<ApplicationResponse> {
    return this.api.invoke(saveMatch, { id });
  }

  dismissMatch(id: string): Observable<JobMatch> {
    return this.api.invoke(dismissMatch, { id });
  }

  restoreMatch(id: string): Observable<JobMatch> {
    return this.api.invoke(restoreMatch, { id });
  }
}
