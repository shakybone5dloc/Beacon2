import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { ApplicationResponse, ApplicationStatus } from '../applications/application.model';
import { Attention, attentionFor } from '../applications/attention';

interface Card {
  app: ApplicationResponse;
  attention: Attention | null;
}

interface Column {
  title: string;
  statuses: ApplicationStatus[];
  cards: Card[];
}

// Active stages get their own column; every terminal status shares "Closed".
const COLUMNS: Omit<Column, 'cards'>[] = [
  { title: 'Saved', statuses: ['SAVED'] },
  { title: 'Applied', statuses: ['APPLIED'] },
  { title: 'Interviewing', statuses: ['INTERVIEWING'] },
  { title: 'Offer', statuses: ['OFFER'] },
  { title: 'Closed', statuses: ['ACCEPTED', 'DECLINED', 'REJECTED', 'WITHDRAWN'] },
];

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

  /** Reference time for ages and countdowns; refreshed on every load and write. */
  private readonly now = signal(new Date());

  protected readonly columns = computed<Column[]>(() => {
    const apps = this.applications() ?? [];
    const now = this.now();

    return COLUMNS.map((column) => ({
      ...column,
      cards: apps
        .filter((app) => column.statuses.includes(app.status))
        .map((app) => ({ app, attention: attentionFor(app, now) })),
    }));
  });

  /** Cards needing attention, overdue follow-ups and closing deadlines first. */
  protected readonly needsAttention = computed(() =>
    this.columns()
      .flatMap((c) => c.cards)
      .filter((card) => card.attention?.needsAttention)
      .sort((a, b) => urgency(a) - urgency(b)),
  );

  ngOnInit(): void {
    this.load();
  }

  protected move(app: ApplicationResponse, target: ApplicationStatus): void {
    this.write(this.api.changeStatus(app.id, target, app.version), 'move');
  }

  protected completeFollowUp(app: ApplicationResponse): void {
    this.write(this.api.scheduleFollowUp(app.id, null, app.version), 'update');
  }

  private write(request: Observable<ApplicationResponse>, verb: string): void {
    this.busy.set(true);
    this.notice.set(null);

    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (updated) => {
        this.now.set(new Date());
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
          this.notice.set(`Could not ${verb} the application. Try again.`);
        }
      },
    });
  }

  private load(): void {
    this.api.list().subscribe({
      next: (apps) => {
        this.now.set(new Date());
        this.applications.set(apps);
      },
      error: () => this.error.set('Could not load applications. Is the API running?'),
    });
  }
}

/** Date-driven reasons sort ahead of age-based ones, earliest date first. */
function urgency(card: Card): number {
  const app = card.app;
  const date = card.attention?.followUp?.urgent
    ? app.followUpOn
    : card.attention?.deadline?.urgent
      ? app.applyBy
      : null;
  return date ? new Date(date).getTime() : Number.MAX_SAFE_INTEGER;
}
