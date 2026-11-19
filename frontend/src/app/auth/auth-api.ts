import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  confirmEmail,
  deleteAccount,
  getCsrfToken,
  getCurrentUser,
  getRegistration,
  login,
  register,
  requestPasswordReset,
  resendEmailConfirmation,
  resetPassword,
} from '../api/functions';
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

  /** Whether signing up needs an invite code, and whether this server can email reset links. */
  registration(): Observable<{ inviteOnly: boolean; passwordReset: boolean }> {
    return this.api.invoke(getRegistration);
  }

  register(request: RegisterRequest): Observable<CurrentUser> {
    return this.api.invoke(register, { body: request });
  }

  /** Emails a reset link if the address has an account; answers the same either way. */
  requestPasswordReset(email: string): Observable<void> {
    return this.api.invoke(requestPasswordReset, { body: { email } });
  }

  /** Sets a new password with the token from a reset link; signs out every other device. */
  resetPassword(token: string, password: string): Observable<void> {
    return this.api.invoke(resetPassword, { body: { token, password } });
  }

  confirmEmail(token: string): Observable<void> {
    return this.api.invoke(confirmEmail, { body: { token } });
  }

  /** Sends the signed-in user another confirmation link. */
  resendConfirmation(): Observable<void> {
    return this.api.invoke(resendEmailConfirmation);
  }

  /** Deletes the account and everything in it, and signs out. */
  deleteAccount(password: string): Observable<void> {
    return this.api.invoke(deleteAccount, { body: { password } });
  }

  /** Handled by Spring Security's filter rather than a controller, so it isn't in the spec. */
  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', null);
  }
}
