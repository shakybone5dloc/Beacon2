import { Component, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { concatMap } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { AuthApi } from './auth-api';
import { AuthState } from './auth-state';

/** Where a confirmation link lands: confirms right away, signed in or not. */
@Component({
  selector: 'app-confirm-email-page',
  imports: [RouterLink],
  template: `
    <section class="auth">
      <h1>Confirm your email</h1>
      @switch (state()) {
        @case ('working') {
          <p class="intro">Confirming...</p>
        }
        @case ('done') {
          <p class="done">Your email is confirmed. Thanks!</p>
          <p class="switch">
            <a [routerLink]="auth.signedIn() ? '/' : '/login'">{{
              auth.signedIn() ? 'Go to Beacon' : 'Sign in'
            }}</a>
          </p>
        }
        @case ('failed') {
          <p class="notice">{{ error() }}</p>
          <p class="switch">
            @if (auth.signedIn()) {
              Use <strong>Resend</strong> in the banner at the top for a new link.
            } @else {
              <a routerLink="/login">Sign in</a> to get a new link.
            }
          </p>
        }
      }
    </section>
  `,
  styleUrl: './auth-page.css',
})
export class ConfirmEmailPage implements OnInit {
  private readonly api = inject(AuthApi);
  protected readonly auth = inject(AuthState);

  /** From the link's query string. */
  readonly token = input<string>();

  protected readonly state = signal<'working' | 'done' | 'failed'>('working');
  protected readonly error = signal('');

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.fail('This page needs the link from your email.');
      return;
    }
    this.api
      .csrf()
      .pipe(concatMap(() => this.api.confirmEmail(token)))
      .subscribe({
        next: () => {
          this.auth.markEmailConfirmed();
          this.state.set('done');
        },
        error: (err: HttpErrorResponse) =>
          this.fail(problemText(err, "Couldn't confirm your email. Try again in a moment.")),
      });
  }

  private fail(message: string): void {
    this.error.set(message);
    this.state.set('failed');
  }
}
