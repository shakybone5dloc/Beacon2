import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CaptureResponse, DocumentKind, DocumentResponse } from './document.model';

/** Documents attached to an application, or kept in the library when no application id is given. */
@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly http = inject(HttpClient);

  list(applicationId: string | null): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(this.base(applicationId));
  }

  upload(
    applicationId: string | null,
    file: File,
    kind: DocumentKind,
  ): Observable<DocumentResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('kind', kind);
    return this.http.post<DocumentResponse>(this.base(applicationId), form);
  }

  delete(applicationId: string | null, documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(applicationId)}/${documentId}`);
  }

  /** For links: PDFs and images open in the browser, other types download. */
  contentUrl(doc: DocumentResponse): string {
    return `${this.base(doc.applicationId)}/${doc.id}/content`;
  }

  /** Prints the application's job URL to PDF and attaches it. */
  capturePosting(applicationId: string): Observable<CaptureResponse> {
    return this.http.post<CaptureResponse>(`${this.base(applicationId)}/posting-capture`, null);
  }

  private base(applicationId: string | null): string {
    return applicationId ? `/api/applications/${applicationId}/documents` : '/api/documents';
  }
}
