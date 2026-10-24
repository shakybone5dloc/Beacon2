import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { JobSource, PostingQuery, PostingSearchResponse } from './posting.model';

@Injectable({ providedIn: 'root' })
export class PostingsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/postings';

  sources(): Observable<JobSource[]> {
    return this.http.get<JobSource[]>(`${this.baseUrl}/sources`);
  }

  search(query: PostingQuery): Observable<PostingSearchResponse> {
    let params = new HttpParams().set('keywords', query.keywords);
    if (query.location) params = params.set('location', query.location);
    if (query.remoteOnly) params = params.set('remoteOnly', 'true');
    if (query.sources.length > 0) params = params.set('sources', query.sources.join(','));

    return this.http.get<PostingSearchResponse>(`${this.baseUrl}/search`, { params });
  }
}
