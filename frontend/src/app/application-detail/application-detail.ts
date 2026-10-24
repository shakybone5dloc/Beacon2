import { Component, computed, inject, input, OnInit, signal, viewChild } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, Observable } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  ApplicationStatus,
  StatusChange,
} from '../applications/application.model';
import { attentionFor } from '../applications/attention';
import { ApplicationDocuments } from '../documents/application-documents';
import { DocumentsApi } from '../documents/documents-api';
import { DocumentResponse } from '../documents/document.model';
import { PostingPanel } from '../posting-view/posting-panel';
import { CoverLetterDrafter } from '../documents/cover-letter-drafter';
import { formatSalary } from '../postings/format';
import { durationBetween } from '../applications/durations';

interface TimelineEntry extends StatusChange {
  /** Time since the previous entry; null for the first. */
  after: string | null;
}

@Component({
  selector: 'app-application-detail',
  imports: [
    DatePipe,
    TitleCasePipe,
    RouterLink,
    ApplicationDocuments,
    CoverLetterDrafter,
    PostingPanel,
  ],
  templateUrl: './application-detail.html',
  styleUrl: './application-detail.css',
})
export class ApplicationDetail implements OnInit {
  private readonly api = inject(ApplicationsApi);
  private readonly router = inject(Router);
  private readonly documentsApi = inject(DocumentsApi);

  /** Bound from the `:id` route param. */
  readonly id = input.required<string>();

  protected readonly application = signal<ApplicationResponse | null>(null);
  protected readonly history = signal<StatusChange[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly deleting = signal(false);

  protected readonly timeInStatus = computed(() => {
    const app = this.application();
    return app ? durationBetween(app.statusChangedAt, new Date()) : null;
  });

  protected readonly documents = viewChild(ApplicationDocuments);
  protected readonly documentList = signal<DocumentResponse[]>([]);
  protected readonly panelOpen = signal(initialPanelOpen());

  /** The newest saved posting (documents arrive newest first), for the panel's PDF tab. */
  protected readonly postingPdfUrl = computed(() => {
    const posting = this.documentList().find(
      (d) => d.kind === 'POSTING' && d.contentType === 'application/pdf',
    );
    return posting ? this.documentsApi.contentUrl(posting) : null;
  });

  protected readonly salary = computed(() => {
    const app = this.application();
    return app ? formatSalary(app) : null;
  });

  protected readonly appliedAgo = computed(() => {
    const appliedAt = this.application()?.appliedAt;
    return appliedAt ? durationBetween(appliedAt, new Date()) : null;
  });

  protected readonly attention = computed(() => {
    const app = this.application();
    return app ? attentionFor(app, new Date()) : null;
  });

  protected readonly timeline = computed<TimelineEntry[]>(() =>
    this.history().map((change, i, all) => ({
      ...change,
      after: i === 0 ? null : durationBetween(all[i - 1].changedAt, change.changedAt),
    })),
  );

  ngOnInit(): void {
    this.load();
  }

  protected togglePanel(): void {
    const open = !this.panelOpen();
    this.panelOpen.set(open);
    try {
      localStorage.setItem(PANEL_KEY, open ? 'open' : 'closed');
    } catch {
      // Storage can be unavailable (private windows); the toggle still works for this visit.
    }
  }

  protected move(app: ApplicationResponse, target: ApplicationStatus): void {
    this.write(this.api.changeStatus(app.id, target, app.version), 'move', () =>
      this.api.history(app.id).subscribe((history) => this.history.set(history)),
    );
  }

  /** A captured posting may have filled in the job description, which also bumps the version. */
  protected onCaptured(descriptionSaved: boolean): void {
    this.notice.set(
      descriptionSaved
        ? 'Posting saved, and its text is now the job description.'
        : 'Posting saved.',
    );
    this.load();
  }

  /** Empty input clears the follow-up. */
  protected scheduleFollowUp(app: ApplicationResponse, date: string): void {
    this.write(this.api.scheduleFollowUp(app.id, date || null, app.version), 'update');
  }

  private write(
    request: Observable<ApplicationResponse>,
    verb: string,
    afterSuccess?: () => void,
  ): void {
    this.busy.set(true);
    this.notice.set(null);

    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (updated) => {
        this.application.set(updated);
        afterSuccess?.();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.notice.set('This application was changed somewhere else. It has been refreshed.');
          this.load();
        } else if (err.status === 404) {
          this.error.set('That application no longer exists.');
        } else {
          this.notice.set(`Could not ${verb} the application. Try again.`);
        }
      },
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

  private load(): void {
    forkJoin({
      application: this.api.getById(this.id()),
      history: this.api.history(this.id()),
    }).subscribe({
      next: ({ application, history }) => {
        this.application.set(application);
        this.history.set(history);
      },
      error: (err: HttpErrorResponse) =>
        this.error.set(
          err.status === 404
            ? 'That application no longer exists.'
            : 'Could not load the application. Is the API running?',
        ),
    });
  }
}

const PANEL_KEY = 'beacon.detail.postingPanel';

/** Remembers the user's last choice; otherwise open on screens wide enough for two columns. */
function initialPanelOpen(): boolean {
  try {
    const saved = localStorage.getItem(PANEL_KEY);
    if (saved) return saved === 'open';
  } catch {
    // Fall through to the screen-size default.
  }
  return typeof window !== 'undefined' && window.innerWidth >= 1100;
}
