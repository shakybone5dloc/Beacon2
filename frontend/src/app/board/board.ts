import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  ApplicationStatus,
  STATUSES,
} from '../applications/application.model';

interface Column {
  status: ApplicationStatus;
  cards: ApplicationResponse[];
}

@Component({
  selector: 'app-board',
  imports: [TitleCasePipe, RouterLink],
  templateUrl: './board.html',
  styleUrl: './board.css',
})
export class Board implements OnInit {
  private readonly api = inject(ApplicationsApi);

  protected readonly applications = signal<ApplicationResponse[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly notice = signal<string | null>(null);

  protected readonly columns = computed<Column[]>(() => {
    const apps = this.applications() ?? [];

    return STATUSES.map((status) => ({
      status,
      cards: apps.filter((app) => app.status == status),
    }));
  });

  ngOnInit(): void {
    this.load();
  }

  protected move(app: ApplicationResponse, target: ApplicationStatus): void {
    this.busy.set(true);
    this.notice.set(null);

    this.api
      .changeStatus(app.id, target, app.version)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (updated) => {
          this.applications.update((list) => list!.map((a) => (a.id === updated.id ? updated : a)));
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409) {
            this.notice.set(
              'This application was changed somewhere else. The board has been refreshed.',
            );
            this.load();
          } else if (err.status === 404) {
            this.notice.set('That application no longer exists. The board has been refreshed.');
            this.load();
          } else {
            this.notice.set('Could not move the application. Try again.');
          }
        },
      });
  }

  private load(): void {
    this.api.list().subscribe({
      next: (apps) => this.applications.set(apps),
      error: () => this.error.set('Could not load applications. Is the API running?'),
    });
  }
}
