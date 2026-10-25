import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthState } from './auth-state';

/** Pages that need a signed-in user send everyone else to sign in, then back. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthState).signedIn()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Sign-in and sign-up pages are pointless once signed in. */
export const guestGuard: CanActivateFn = () => {
  if (!inject(AuthState).signedIn()) return true;
  return inject(Router).createUrlTree(['/']);
};

/** Only paths inside Beacon, so a crafted link can't bounce the user to another site. */
export function safeReturnUrl(value: string | null | undefined): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\'))
    return '/';
  return value;
}
