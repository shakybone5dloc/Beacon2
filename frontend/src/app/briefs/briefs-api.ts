import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { chooseBriefResume, getBrief, saveBriefSection, writeBriefSection } from '../api/functions';
import { Brief, BriefSection, SectionKind } from './brief.model';

/** An application's interview brief: its sections, written by the AI or by hand. */
@Injectable({ providedIn: 'root' })
export class BriefsApi {
  private readonly api = inject(BeaconApi);

  get(applicationId: string): Observable<Brief> {
    return this.api.invoke(getBrief, { applicationId });
  }

  chooseResume(applicationId: string, resumeDocumentId: string): Observable<Brief> {
    return this.api.invoke(chooseBriefResume, { applicationId, body: { resumeDocumentId } });
  }

  /** A section with the user's edits answers 409 unless {@code replaceEdits} is set. */
  write(
    applicationId: string,
    kind: SectionKind,
    interviewId: string | null,
    replaceEdits = false,
  ): Observable<BriefSection> {
    return this.api.invoke(writeBriefSection, {
      applicationId,
      kind,
      interviewId: interviewId ?? undefined,
      replaceEdits,
    });
  }

  save(applicationId: string, section: BriefSection, content: string): Observable<BriefSection> {
    return this.api.invoke(saveBriefSection, {
      applicationId,
      kind: section.kind,
      interviewId: section.interviewId ?? undefined,
      body: { content, version: section.version },
    });
  }
}
