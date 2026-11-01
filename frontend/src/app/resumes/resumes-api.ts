import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { getResumeText, listResumes, makeDefaultResume, updateResume } from '../api/functions';
import { Resume, UpdateResumeRequest } from './resume.model';

/** The library's resumes. Uploading and deleting go through DocumentsApi. */
@Injectable({ providedIn: 'root' })
export class ResumesApi {
  private readonly api = inject(BeaconApi);

  /** The default first, then newest first. */
  list(): Observable<Resume[]> {
    return this.api.invoke(listResumes);
  }

  update(documentId: string, request: UpdateResumeRequest): Observable<Resume> {
    return this.api.invoke(updateResume, { documentId, body: request });
  }

  makeDefault(documentId: string): Observable<Resume> {
    return this.api.invoke(makeDefaultResume, { documentId });
  }

  /** What Beacon read from the file; null when it has no readable text. */
  text(documentId: string): Observable<string | null> {
    return this.api.invoke(getResumeText, { documentId }).pipe(map((r) => r.text));
  }
}
