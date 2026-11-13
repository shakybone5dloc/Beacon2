import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { draftCheckIn, getSendFrom, saveSendFrom, startCheckIn } from '../api/functions';
import { browserTimeZone } from '../interviews/interview.model';
import { CheckIn, CheckInDraft, SendFrom } from './check-in.model';

/** Check-in emails: the filled-in template, an AI draft, and the address they're sent from. */
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

  sendFrom(): Observable<SendFrom> {
    return this.api.invoke(getSendFrom);
  }

  /** @param address null to send from the sign-in address */
  saveSendFrom(address: string | null): Observable<SendFrom> {
    return this.api.invoke(saveSendFrom, { body: { sendFrom: address } });
  }
}
