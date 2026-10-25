import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthState } from './auth-state';

/** These answer 401 as part of their normal job, so they don't mean the session ended. */
const EXPECTS_401 = ['/api/auth/login', '/api/auth/me'];

/** A 401 from the API means the session ended: forget the user and go to sign-in. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthState);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (
        err instanceof HttpErrorResponse &&
        err.status === 401 &&
        !EXPECTS_401.includes(req.url)
      ) {
        auth.clear();
        const here = router.url;
        router.navigate(['/login'], {
          queryParams: here.startsWith('/login') ? {} : { returnUrl: here },
        });
      }
      return throwError(() => err);
    }),
  );
};
