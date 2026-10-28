import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { getCsrfToken, getCurrentUser, login, register } from '../api/functions';
import { CurrentUser, LoginRequest, RegisterRequest } from './auth.model';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly api = inject(BeaconApi);
  private readonly http = inject(HttpClient);

  /** Makes the server set the XSRF-TOKEN cookie that Angular sends back on writes. */
  csrf(): Observable<void> {
    return this.api.invoke(getCsrfToken);
  }

  me(): Observable<CurrentUser> {
    return this.api.invoke(getCurrentUser);
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.api.invoke(login, { body: request });
  }

  register(request: RegisterRequest): Observable<CurrentUser> {
    return this.api.invoke(register, { body: request });
  }

  /** Handled by Spring Security's filter rather than a controller, so it isn't in the spec. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null);
  }
}
