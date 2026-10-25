import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { AuthState } from './auth/auth-state';
import { authInterceptor } from './auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Angular's XSRF support is on by default: it copies the XSRF-TOKEN cookie into X-XSRF-TOKEN.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // Know who is signed in before the first route's guard runs.
    provideAppInitializer(() => inject(AuthState).load()),
  ],
};
