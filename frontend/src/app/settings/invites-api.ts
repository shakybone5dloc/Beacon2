import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { createInvite, listInvites, revokeInvite } from '../api/functions';
import * as api from '../api/models';

export type Invite = api.InviteResponse;

/** Invite codes for the invite-only beta; admins only. */
@Injectable({ providedIn: 'root' })
export class InvitesApi {
  private readonly api = inject(BeaconApi);

  list(): Observable<Invite[]> {
    return this.api.invoke(listInvites);
  }

  create(note: string | null, days: number): Observable<Invite> {
    return this.api.invoke(createInvite, { body: { note, days } });
  }

  revoke(id: string): Observable<Invite> {
    return this.api.invoke(revokeInvite, { id });
  }
}

/** The link that opens the sign-up page with the code filled in. */
export function inviteLink(code: string, origin = window.location.origin): string {
  return `${origin}/register?invite=${encodeURIComponent(code)}`;
}
