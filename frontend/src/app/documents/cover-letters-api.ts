import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { draftCoverLetter, getCoverLetterStatus, saveCoverLetter } from '../api/functions';
import { DocumentResponse } from './document.model';

@Injectable({ providedIn: 'root' })
export class CoverLettersApi {
  private readonly api = inject(BeaconApi);

  /** False until the user saves an OpenAI or Anthropic API key. */
  isConfigured(): Observable<boolean> {
    return this.api.invoke(getCoverLetterStatus).pipe(map((r) => r.configured));
  }

  draft(
    applicationId: string,
    resumeDocumentId: string,
    instructions: string | null,
  ): Observable<string> {
    return this.api
      .invoke(draftCoverLetter, { applicationId, body: { resumeDocumentId, instructions } })
      .pipe(map((r) => r.text));
  }

  /** Saves the letter as a PDF cover letter document. */
  save(applicationId: string, text: string): Observable<DocumentResponse> {
    return this.api.invoke(saveCoverLetter, { applicationId, body: { text } });
  }
}
