import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  getApplicationFit,
  getFitStatus,
  scoreApplicationFit,
  scorePostingFit,
} from '../api/functions';
import { Fit, ScorePostingFitRequest } from './fit.model';

/** AI fit checks: a resume against an application's posting or a job search result. */
@Injectable({ providedIn: 'root' })
export class FitApi {
  private readonly api = inject(BeaconApi);

  /** True when this user has an AI key to check fit with. */
  isConfigured(): Observable<boolean> {
    return this.api.invoke(getFitStatus).pipe(map((r) => r.configured));
  }

  /** The application's latest fit; null until it's been checked. */
  forApplication(applicationId: string): Observable<Fit | null> {
    return this.api.invoke(getApplicationFit, { applicationId }).pipe(map((r) => r.fit));
  }

  /** Checking the same posting and resume again reuses the kept score at no cost. */
  scoreApplication(applicationId: string, resumeDocumentId: string): Observable<Fit> {
    return this.api.invoke(scoreApplicationFit, { applicationId, body: { resumeDocumentId } });
  }

  scorePosting(request: ScorePostingFitRequest): Observable<Fit> {
    return this.api.invoke(scorePostingFit, { body: request });
  }
}
