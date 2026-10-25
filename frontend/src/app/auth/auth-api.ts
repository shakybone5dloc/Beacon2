import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CurrentUser, LoginRequest, RegisterRequest } from './auth.model';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/auth';

  /** Makes the server set the XSRF-TOKEN cookie that Angular sends back on writes. */
  csrf(): Observable<void> {
    return this.http.get<void>(`${this.baseUrl}/csrf`);
  }

  me(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${this.baseUrl}/me`);
  }

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.http.post<CurrentUser>(`${this.baseUrl}/login`, request);
  }

  register(request: RegisterRequest): Observable<CurrentUser> {
    return this.http.post<CurrentUser>(`${this.baseUrl}/register`, request);
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, null);
  }
}
