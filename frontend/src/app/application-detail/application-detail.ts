import { Component, inject, input, OnInit, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { ApplicationResponse } from '../applications/application.model';

@Component({
  selector: 'app-application-detail',
  imports: [DatePipe, TitleCasePipe, RouterLink],
  templateUrl: './application-detail.html',
  styleUrl: './application-detail.css',
})
export class ApplicationDetail implements OnInit {
  private readonly api = inject(ApplicationsApi);
  private readonly router = inject(Router);

  /** Bound from the `:id` route param. */
  readonly id = input.required<string>();

  protected readonly application = signal<ApplicationResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly deleting = signal(false);

  ngOnInit(): void {
    this.api.getById(this.id()).subscribe({
      next: (app) => this.application.set(app),
      error: (err: HttpErrorResponse) =>
        this.error.set(
          err.status === 404
            ? 'That application no longer exists.'
            : 'Could not load the application. Is the API running?',
        ),
    });
  }

  protected remove(app: ApplicationResponse): void {
    if (!confirm(`Delete ${app.company} – ${app.role}? This cannot be undone.`)) {
      return;
    }

    this.deleting.set(true);
    this.notice.set(null);

    this.api
      .delete(app.id)
      .pipe(finalize(() => this.deleting.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/']),
        error: (err: HttpErrorResponse) => {
          if (err.status === 404) {
            // Already gone, which is what the user wanted.
            this.router.navigate(['/']);
          } else {
            this.notice.set('Could not delete the application. Try again.');
          }
        },
      });
  }
}
