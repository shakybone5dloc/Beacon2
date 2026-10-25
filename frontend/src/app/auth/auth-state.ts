import { computed, inject, Injectable, signal } from '@angular/core';
import { catchError, concatMap, finalize, map, Observable, of, tap } from 'rxjs';
import { AuthApi } from './auth-api';
import { CurrentUser, LoginRequest, RegisterRequest } from './auth.model';

/** Who is signed in. Loaded once at startup; null when nobody is. */
@Injectable({ providedIn: 'root' })
export class AuthState {
  private readonly api = inject(AuthApi);
  private readonly current = signal<CurrentUser | null>(null);

  readonly user = this.current.asReadonly();
  readonly signedIn = computed(() => this.current() != null);

  /** Primes the CSRF cookie, then asks who is signed in. Never fails. */
  load(): Observable<CurrentUser | null> {
    return this.api.csrf().pipe(
      concatMap(() => this.api.me()),
      catchError(() => of(null)),
      tap((user) => this.current.set(user)),
    );
  }

  // Signing out clears the CSRF cookie, so sign-in and sign-up fetch a fresh one first.

  login(request: LoginRequest): Observable<CurrentUser> {
    return this.api.csrf().pipe(
      concatMap(() => this.api.login(request)),
      tap((user) => this.current.set(user)),
    );
  }

  register(request: RegisterRequest): Observable<CurrentUser> {
    return this.api.csrf().pipe(
      concatMap(() => this.api.register(request)),
      tap((user) => this.current.set(user)),
    );
  }

  /** Forgets the user even if the server call fails; the session is no use to them either way. */
  logout(): Observable<void> {
    return this.api.logout().pipe(
      catchError(() => of(undefined)),
      map(() => undefined),
      finalize(() => this.current.set(null)),
    );
  }

  /** For when the server says the session has ended. */
  clear(): void {
    this.current.set(null);
  }
}
