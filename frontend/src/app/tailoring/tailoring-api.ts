import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  getApplicationResume,
  markResumeSuggestion,
  setSentResume,
  suggestResumeChanges,
} from '../api/functions';
import { ResumeSuggestion, SuggestionState, Tailoring } from './tailoring.model';

/** The resume sent with an application, and AI suggestions to tailor one to its posting. */
@Injectable({ providedIn: 'root' })
export class TailoringApi {
  private readonly api = inject(BeaconApi);

  get(applicationId: string): Observable<Tailoring> {
    return this.api.invoke(getApplicationResume, { applicationId });
  }

  /** New suggestions replace the open and dismissed ones; those done are kept. */
  suggest(applicationId: string, resumeDocumentId: string | null): Observable<Tailoring> {
    return this.api.invoke(suggestResumeChanges, {
      applicationId,
      body: { resumeDocumentId },
    });
  }

  mark(
    applicationId: string,
    suggestionId: string,
    state: SuggestionState,
  ): Observable<ResumeSuggestion> {
    return this.api.invoke(markResumeSuggestion, { applicationId, suggestionId, body: { state } });
  }

  /** Null clears it. */
  setSent(applicationId: string, resumeDocumentId: string | null): Observable<Tailoring> {
    return this.api.invoke(setSentResume, {
      applicationId,
      body: { resumeDocumentId },
    });
  }
}
