import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { draftCheckIn, getMailClient, saveMailClient, startCheckIn } from '../api/functions';
import { browserTimeZone } from '../interviews/interview.model';
import { CheckIn, CheckInDraft, MailClient } from './check-in.model';

/** Check-in emails: the filled-in template, an AI draft, and where the user's email opens. */
@Injectable({ providedIn: 'root' })
export class CheckInApi {
  private readonly api = inject(BeaconApi);

  start(applicationId: string): Observable<CheckIn> {
    return this.api.invoke(startCheckIn, { applicationId, zone: browserTimeZone() });
  }

  /** Has the AI write the email from the application's history, improving on `body`. */
  draft(applicationId: string, recipient: string, body: string): Observable<CheckInDraft> {
    return this.api.invoke(draftCheckIn, {
      applicationId,
      zone: browserTimeZone(),
      body: { recipient, body },
    });
  }

  mailClient(): Observable<MailClient> {
    return this.api.invoke(getMailClient).pipe(map((r) => r.mailClient));
  }

  saveMailClient(mailClient: MailClient): Observable<MailClient> {
    return this.api.invoke(saveMailClient, { body: { mailClient } }).pipe(map((r) => r.mailClient));
  }
}
