import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { listJobSources, searchPostings } from '../api/functions';
import { JobSource, PostingQuery, PostingSearchResponse } from './posting.model';

@Injectable({ providedIn: 'root' })
export class PostingsApi {
  private readonly api = inject(BeaconApi);

  sources(): Observable<JobSource[]> {
    return this.api.invoke(listJobSources);
  }

  /** Filters that aren't set are left out of the query string. */
  search(query: PostingQuery): Observable<PostingSearchResponse> {
    return this.api.invoke(searchPostings, {
      keywords: query.keywords,
      location: query.location || undefined,
      remoteOnly: query.remoteOnly || undefined,
      sources: query.sources.length > 0 ? query.sources : undefined,
    });
  }
}
