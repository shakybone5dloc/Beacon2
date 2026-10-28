import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  capturePosting,
  deleteApplicationDocument,
  deleteLibraryDocument,
  downloadApplicationDocument,
  downloadLibraryDocument,
  listApplicationDocuments,
  listLibraryDocuments,
  savePostingSummary,
  uploadApplicationDocument,
  uploadLibraryDocument,
} from '../api/functions';
import { CaptureResponse, DocumentKind, DocumentResponse } from './document.model';

/** Documents attached to an application, or kept in the library when no application id is given. */
@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly api = inject(BeaconApi);

  list(applicationId: string | null): Observable<DocumentResponse[]> {
    return applicationId
      ? this.api.invoke(listApplicationDocuments, { applicationId })
      : this.api.invoke(listLibraryDocuments);
  }

  upload(
    applicationId: string | null,
    file: File,
    kind: DocumentKind,
  ): Observable<DocumentResponse> {
    const body = { file };
    return applicationId
      ? this.api.invoke(uploadApplicationDocument, { applicationId, kind, body })
      : this.api.invoke(uploadLibraryDocument, { kind, body });
  }

  delete(applicationId: string | null, documentId: string): Observable<void> {
    return applicationId
      ? this.api.invoke(deleteApplicationDocument, { applicationId, documentId })
      : this.api.invoke(deleteLibraryDocument, { documentId });
  }

  /** For links: PDFs and images open in the browser, other types download. */
  contentUrl(doc: DocumentResponse): string {
    return doc.applicationId
      ? downloadApplicationDocument.PATH.replace('{applicationId}', doc.applicationId).replace(
          '{documentId}',
          doc.id,
        )
      : downloadLibraryDocument.PATH.replace('{documentId}', doc.id);
  }

  /** Prints the application's job URL to PDF and attaches it. */
  capturePosting(applicationId: string): Observable<CaptureResponse> {
    return this.api.invoke(capturePosting, { applicationId });
  }

  /** Renders Beacon's own posting view to PDF on the server and attaches it. */
  savePostingSummary(applicationId: string): Observable<DocumentResponse> {
    return this.api.invoke(savePostingSummary, { applicationId });
  }
}
