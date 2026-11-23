import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { disableUser, enableUser, listUsers } from '../api/functions';
import * as api from '../api/models';

export type AdminUser = api.AdminUserResponse;

/** Every account, and switching one off or on; admins only. */
@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly api = inject(BeaconApi);

  list(): Observable<AdminUser[]> {
    return this.api.invoke(listUsers);
  }

  disable(id: string): Observable<AdminUser> {
    return this.api.invoke(disableUser, { id });
  }

  enable(id: string): Observable<AdminUser> {
    return this.api.invoke(enableUser, { id });
  }
}
