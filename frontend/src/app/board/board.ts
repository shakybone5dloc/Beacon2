import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { finalize, Observable } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  ApplicationStatus,
  EMPLOYMENT_TYPE_LABELS,
  employerLabel,
  isForwardMove,
  orderedMoves,
} from '../applications/application.model';
import { Attention, attentionFor } from '../applications/attention';
import { formatLocation, formatSalary, withoutRemoteNote } from '../postings/format';
import { verdict, Verdict } from '../fit/fit.model';

interface Card {
  app: ApplicationResponse;
  attention: Attention | null;
}

interface Column {
  title: string;
  statuses: ApplicationStatus[];
  cards: Card[];
}

/** A short fact on a card, with its full text for the tooltip when it's cut off. */
interface CardTag {
  text: string;
  title: string;
}

function tag(text: string | null): CardTag | null {
  return text ? { text, title: text } : null;
}

/** A card dropped on a column it can reach in more than one way (Closed: rejected, withdrawn...). */
interface PendingDrop {
  app: ApplicationResponse;
  column: string;
  targets: ApplicationStatus[];
}

// Active stages get their own column; every terminal status shares "Closed".
const COLUMNS: Omit<Column, 'cards'>[] = [
  { title: 'Saved', statuses: ['SAVED'] },
  { title: 'Applied', statuses: ['APPLIED'] },
  { title: 'Interviewing', statuses: ['INTERVIEWING'] },
  { title: 'Offer', statuses: ['OFFER'] },
  { title: 'Closed', statuses: ['ACCEPTED', 'DECLINED', 'REJECTED', 'WITHDRAWN'] },
];

const ACTIVE: ApplicationStatus[] = ['SAVED', 'APPLIED', 'INTERVIEWING', 'OFFER'];

/** How a closed application's outcome badge is coloured. */
const OUTCOME_CLASSES: Partial<Record<ApplicationStatus, string>> = {
  ACCEPTED: 'pill-green',
  REJECTED: 'pill-red',
};

@Component({
  selector: 'app-board',
  imports: [TitleCasePipe, RouterLink, CdkDropListGroup, CdkDropList, CdkDrag],
  templateUrl: './board.html',
  styleUrl: './board.css',
})
export class Board implements OnInit {
  private readonly api = inject(ApplicationsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly employerLabel = employerLabel;
  /** A forward move is the primary button, with an arrow after its label. */
  protected readonly forwardClasses = "btn-primary after:content-['_→']";
  protected readonly verdict = verdict;
  /** The fit bar's fill, in the verdict's colour. */
  protected readonly fitFill: Record<Verdict['pill'], string> = {
    'pill-green': 'bg-success',
    'pill-blue': 'bg-info',
    'pill-amber': 'bg-attention',
    'pill-red': 'bg-error',
  };

  protected readonly applications = signal<ApplicationResponse[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly notice = signal<string | null>(null);

  /** Reference time for ages and countdowns; refreshed on every load and write. */
  private readonly now = signal(new Date());

  // Filters live in the address, so a refresh keeps them and the Board link clears them.
  protected readonly search = signal('');
  protected readonly source = signal('');
  protected readonly remoteOnly = signal(false);
  protected readonly needsActionOnly = signal(false);

  protected readonly filtering = computed(
    () => !!this.search().trim() || !!this.source() || this.remoteOnly() || this.needsActionOnly(),
  );

  /** Every source on the board, for the picker. */
  protected readonly sources = computed(() =>
    [
      ...new Set((this.applications() ?? []).map((a) => a.source).filter((s): s is string => !!s)),
    ].sort((a, b) => a.localeCompare(b)),
  );

  private readonly cards = computed<Card[]>(() => {
    const now = this.now();
    const words = searchWords(this.search());
    const source = this.source();
    return (this.applications() ?? [])
      .map((app) => ({ app, attention: attentionFor(app, now) }))
      .filter(
        (card) =>
          matchesWords(card.app, words) &&
          (!source || card.app.source === source) &&
          (!this.remoteOnly() || card.app.remote) &&
          (!this.needsActionOnly() || !!card.attention?.needsAttention),
      );
  });

  protected readonly shownCount = computed(() => this.cards().length);
  protected readonly totalCount = computed(() => (this.applications() ?? []).length);

  protected readonly columns = computed<Column[]>(() =>
    COLUMNS.map((column) => ({
      ...column,
      cards: this.cards().filter((card) => column.statuses.includes(card.app.status)),
    })),
  );

  /** Waiting for the user to say which way to close a card dropped on Closed. */
  protected readonly pendingDrop = signal<PendingDrop | null>(null);

  protected readonly activeCount = computed(
    () => (this.applications() ?? []).filter((app) => ACTIVE.includes(app.status)).length,
  );

  /** Cards needing attention, overdue follow-ups and closing deadlines first. */
  protected readonly needsAttention = computed(() =>
    this.columns()
      .flatMap((c) => c.cards)
      .filter((card) => card.attention?.needsAttention)
      .sort((a, b) => urgency(a) - urgency(b)),
  );

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.search.set(params.get('q') ?? '');
    this.source.set(params.get('source') ?? '');
    this.remoteOnly.set(params.get('remote') === 'true');
    this.needsActionOnly.set(params.get('action') === 'true');
    this.load();
  }

  protected setSearch(value: string): void {
    this.search.set(value);
    this.saveFilters();
  }

  protected setSource(value: string): void {
    this.source.set(value);
    this.saveFilters();
  }

  protected setRemoteOnly(value: boolean): void {
    this.remoteOnly.set(value);
    this.saveFilters();
  }

  protected setNeedsActionOnly(value: boolean): void {
    this.needsActionOnly.set(value);
    this.saveFilters();
  }

  protected clearFilters(): void {
    this.search.set('');
    this.source.set('');
    this.remoteOnly.set(false);
    this.needsActionOnly.set(false);
    this.saveFilters();
  }

  private saveFilters(): void {
    const q = this.search().trim();
    this.router.navigate([], {
      relativeTo: this.route,
      replaceUrl: true,
      queryParams: {
        q: q || null,
        source: this.source() || null,
        remote: this.remoteOnly() || null,
        action: this.needsActionOnly() || null,
      },
    });
  }

  /** A column takes a card only if one of the card's allowed moves lands in it. */
  protected readonly canDrop = (
    drag: CdkDrag<ApplicationResponse>,
    drop: CdkDropList<Column>,
  ): boolean => drop.data.statuses.some((status) => drag.data.allowedTransitions.includes(status));

  protected dropped(event: CdkDragDrop<Column, Column, ApplicationResponse>): void {
    if (event.previousContainer === event.container) return;
    const app = event.item.data;
    const targets = event.container.data.statuses.filter((s) => app.allowedTransitions.includes(s));
    if (targets.length === 1) {
      this.move(app, targets[0]);
    } else if (targets.length > 1) {
      this.pendingDrop.set({ app, column: event.container.data.title, targets });
    }
  }

  protected finishDrop(target: ApplicationStatus | null): void {
    const pending = this.pendingDrop();
    this.pendingDrop.set(null);
    if (pending && target) this.move(pending.app, target);
  }

  protected readonly isForward = isForwardMove;
  protected readonly moves = orderedMoves;

  protected outcomeClass(status: ApplicationStatus): string {
    return OUTCOME_CLASSES[status] ?? '';
  }

  /**
   * Short facts for the card's tags: where, how and what it pays. The location drops a
   * "(remote job)" note when the Remote tag is there to say it, and keeps the full text
   * for the tooltip, since a long tag is cut off to fit the card.
   */
  protected details(app: ApplicationResponse): CardTag[] {
    const where = app.remote ? withoutRemoteNote(app.location) : app.location;
    const location = formatLocation(where, 2);
    const tags = [
      location ? { text: location, title: app.location ?? location } : null,
      tag(app.remote ? 'Remote' : null),
      tag(app.employmentType ? EMPLOYMENT_TYPE_LABELS[app.employmentType] : null),
      tag(formatSalary(app)),
    ];
    return tags.filter((t): t is CardTag => t != null);
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

/** Lower-case words without accents, so "Sr Java" finds "Sr. Java" and "Montréal" finds "montreal". */
function searchWords(text: string): string[] {
  return plain(text)
    .split(/\s+/)
    .filter((w) => w.length > 0);
}

function plain(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase();
}

/** Every word appears in the employer, role, location, source or notes. */
function matchesWords(app: ApplicationResponse, words: string[]): boolean {
  if (words.length === 0) return true;
  const haystack = plain(
    [employerLabel(app), app.role, app.location, app.source, app.notes].filter(Boolean).join(' '),
  );
  return words.every((word) => haystack.includes(word));
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
