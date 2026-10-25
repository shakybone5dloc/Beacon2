import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { DocumentResponse } from './document.model';

@Injectable({ providedIn: 'root' })
export class CoverLettersApi {
  private readonly http = inject(HttpClient);

  /** False until an OpenAI or Anthropic API key is configured on the server. */
  isConfigured(): Observable<boolean> {
    return this.http
      .get<{ configured: boolean }>('/api/cover-letters/status')
      .pipe(map((r) => r.configured));
  }

  draft(
    applicationId: string,
    resumeDocumentId: string,
    instructions: string | null,
  ): Observable<string> {
    return this.http
      .post<{ text: string }>(`/api/applications/${applicationId}/cover-letter/draft`, {
        resumeDocumentId,
        instructions,
      })
      .pipe(map((r) => r.text));
  }

  /** Saves the letter as a PDF cover letter document. */
  save(applicationId: string, text: string): Observable<DocumentResponse> {
    return this.http.post<DocumentResponse>(`/api/applications/${applicationId}/cover-letter`, {
      text,
    });
  }
}
