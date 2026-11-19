import {
  afterNextRender,
  Component,
  ElementRef,
  inject,
  input,
  OnInit,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { formatLocation, formatSalary } from '../postings/format';
import { SearchesApi } from './searches-api';
import { JobMatch } from './search.model';

/**
 * A match's whole posting in a dialog, so it can be read before deciding to save it to
 * the board. The parent shows it while a match is chosen and removes it on (closed).
 */
@Component({
  selector: 'app-match-preview',
  template: `
    <dialog
      #dialog
      class="match-preview m-auto max-h-[min(48rem,calc(100dvh-2rem))] w-[min(44rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-lg border border-hairline bg-surface-container-lowest p-0 text-on-surface shadow-xl open:flex backdrop:bg-black/50"
      aria-labelledby="match-preview-title"
      (close)="closed.emit()"
      (click)="backdropClick($event)"
    >
      <header class="flex items-start gap-space-md border-b border-hairline p-space-lg">
        <div class="min-w-0 flex-1">
          <h2 id="match-preview-title" class="m-0 text-headline-md">{{ match().title }}</h2>
          <p class="m-0 mt-space-xs text-body-sm text-on-surface-variant">
            {{ match().company ?? 'Employer not disclosed' }}
            @if (location(); as where) {
              · {{ where }}
            }
            @if (match().remote) {
              · Remote
            }
          </p>
          <div class="mt-space-sm flex flex-wrap gap-space-xs">
            @if (salary(); as pay) {
              <span class="pill">{{ pay }}</span>
            }
            @if (match().closesOn) {
              <span class="pill pill-amber">apply by {{ match().closesOn }}</span>
            }
            @if (match().score != null) {
              <span class="pill pill-blue">fit {{ match().score }}</span>
            }
          </div>
        </div>
        <button
          type="button"
          class="close h-8 w-8 shrink-0 border-0 bg-transparent p-0 text-on-surface-variant hover:text-on-surface"
          aria-label="Close"
          (click)="close()"
        >
          <span class="material-symbols-outlined" aria-hidden="true">close</span>
        </button>
      </header>

      <div class="body min-h-0 flex-1 overflow-y-auto p-space-lg">
        @if (failed()) {
          <p class="m-0 text-error">Couldn't load the posting. Try the original link below.</p>
        } @else if (description() === undefined) {
          <p class="m-0 text-on-surface-variant">Loading...</p>
        } @else if (description(); as text) {
          <p class="description m-0 text-body-md whitespace-pre-line">{{ text }}</p>
        } @else {
          <p class="m-0 text-body-md whitespace-pre-line">{{ match().snippet }}</p>
          <p class="m-0 mt-space-md text-body-sm text-on-surface-variant">
            This source only sent a short summary. The full posting is on its site.
          </p>
        }
      </div>

      <footer
        class="flex flex-wrap items-center gap-space-sm border-t border-hairline px-space-lg py-space-md"
      >
        <a [href]="match().url" target="_blank" rel="noopener noreferrer" class="text-body-sm"
          >Open the original ↗</a
        >
        @if (actions()) {
          <span class="ml-auto flex gap-space-sm">
            <button type="button" class="dismiss btn" [disabled]="busy()" (click)="dismiss.emit()">
              Dismiss
            </button>
            <button
              type="button"
              class="save btn btn-primary"
              [disabled]="busy()"
              (click)="save.emit()"
            >
              Save to board
            </button>
          </span>
        }
      </footer>
    </dialog>
  `,
})
export class MatchPreview implements OnInit {
  private readonly api = inject(SearchesApi);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly match = input.required<JobMatch>();
  /** Shows Save and Dismiss; off for matches already saved or dismissed. */
  readonly actions = input(true);
  readonly busy = input(false);

  readonly save = output<void>();
  readonly dismiss = output<void>();
  readonly closed = output<void>();

  /** undefined while loading, null when the source gave no full text. */
  protected readonly description = signal<string | null | undefined>(undefined);
  protected readonly failed = signal(false);

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  ngOnInit(): void {
    this.api.matchPosting(this.match().id).subscribe({
      next: (p) => this.description.set(p.description?.trim() || null),
      error: () => this.failed.set(true),
    });
  }

  protected location(): string | null {
    return formatLocation(this.match().location);
  }

  protected salary(): string | null {
    return formatSalary(this.match());
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  /** A click on the dimmed area outside the box lands on the dialog element itself. */
  protected backdropClick(event: MouseEvent): void {
    if (event.target === this.dialog().nativeElement) this.close();
  }
}
