import {
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { finalize, Observable } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  ApplicationStatus,
  employerLabel,
  EMPLOYMENT_TYPE_LABELS,
  isForwardMove,
  orderedMoves,
} from '../applications/application.model';
import { attentionFor } from '../applications/attention';
import { ApplicationDocuments } from '../documents/application-documents';
import { DocumentsApi } from '../documents/documents-api';
import { DocumentResponse } from '../documents/document.model';
import { ApplicationPeople } from '../people/application-people';
import { ContactLog } from '../contacts/contact-log';
import { ApplicationInterviews } from '../interviews/application-interviews';
import { PossibleDuplicates } from '../contacts/possible-duplicates';
import { PostingPanel } from '../posting-view/posting-panel';
import { ApplicationTimeline } from '../timeline/application-timeline';
import { CoverLetterDrafter } from '../documents/cover-letter-drafter';
import { ApplicationFit } from '../fit/application-fit';
import { formatSalary } from '../postings/format';
import { durationBetween } from '../applications/durations';

@Component({
  selector: 'app-application-detail',
  imports: [
    DatePipe,
    TitleCasePipe,
    RouterLink,
    ApplicationDocuments,
    CoverLetterDrafter,
    PostingPanel,
    ApplicationPeople,
    ContactLog,
    PossibleDuplicates,
    ApplicationInterviews,
    ApplicationTimeline,
    ApplicationFit,
  ],
  templateUrl: './application-detail.html',
  styleUrl: './application-detail.css',
})
export class ApplicationDetail {
  private readonly api = inject(ApplicationsApi);
  protected readonly employerLabel = employerLabel;
  protected readonly isForward = isForwardMove;
  protected readonly moves = orderedMoves;
  protected readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;
  private readonly router = inject(Router);
  private readonly documentsApi = inject(DocumentsApi);

  /** Bound from the `:id` route param. */
  readonly id = input.required<string>();

  protected readonly application = signal<ApplicationResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly deleting = signal(false);

  protected readonly timeInStatus = computed(() => {
    const app = this.application();
    return app ? durationBetween(app.statusChangedAt, new Date()) : null;
  });

  protected readonly documents = viewChild(ApplicationDocuments);
  protected readonly people = viewChild(ApplicationPeople);
  protected readonly timeline = viewChild(ApplicationTimeline);
  protected readonly documentList = signal<DocumentResponse[]>([]);
  private documentsLoaded = false;
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

  constructor() {
    // The route reuses this page when going from one application to another (a
    // possible duplicate's link), so load whenever the id changes.
    effect(() => {
      this.id();
      untracked(() => this.load());
    });
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
      this.timeline()?.reload(),
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
    if (!confirm(`Delete ${employerLabel(app)} – ${app.role}? This cannot be undone.`)) {
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

  /** People changed: a recruiter's agency may now show for an undisclosed employer. */
  protected peopleChanged(): void {
    this.reloadApplication();
    this.timeline()?.reload();
  }

  /** Documents changed: the posting PDF may be new, and the timeline lists documents. */
  protected documentsChanged(documents: DocumentResponse[]): void {
    // The first call is the list loading, which the timeline already covers.
    if (this.documentsLoaded) this.timeline()?.reload();
    this.documentsLoaded = true;
    this.documentList.set(documents);
  }

  protected reloadApplication(): void {
    this.api.getById(this.id()).subscribe((application) => this.application.set(application));
  }

  /** The next interview may have moved, and new interviewers are now on the application. */
  protected interviewsChanged(): void {
    this.reloadApplication();
    this.people()?.reload();
    this.timeline()?.reload();
  }

  /** Someone logged a contact, which can also put the person on the application. */
  protected contactLogged(): void {
    this.people()?.reload();
    this.timeline()?.reload();
  }

  private load(): void {
    this.api.getById(this.id()).subscribe({
      next: (application) => this.application.set(application),
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
