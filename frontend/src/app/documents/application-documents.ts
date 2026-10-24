import { Component, computed, effect, inject, input, OnInit, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { DocumentsApi } from './documents-api';
import {
  ACCEPTED_FILES,
  DOCUMENT_KINDS,
  DocumentKind,
  DocumentResponse,
  KIND_LABELS,
} from './document.model';
import { ProblemDetail } from '../applications/application.model';
import { formatBytes } from './format-bytes';

/**
 * Files kept with one application, or the library when applicationId is null:
 * list, upload, delete, and (for applications) save the posting page as PDF.
 */
@Component({
  selector: 'app-application-documents',
  imports: [DatePipe],
  templateUrl: './application-documents.html',
  styleUrl: './application-documents.css',
})
export class ApplicationDocuments implements OnInit {
  private readonly api = inject(DocumentsApi);

  readonly applicationId = input<string | null>(null);
  /** The application's posting URL; enables "Save posting as PDF". */
  readonly jobUrl = input<string | null>(null);
  /** Kind preselected for uploads. */
  readonly defaultKind = input<DocumentKind>('RESUME');

  /** After a posting capture; true when its text became the job description. */
  readonly captured = output<boolean>();
  /** The current list, whenever it loads or changes. */
  readonly changed = output<DocumentResponse[]>();

  protected readonly documents = signal<DocumentResponse[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly uploading = signal(false);
  protected readonly capturing = signal(false);
  protected readonly deleting = signal<ReadonlySet<string>>(new Set());
  protected readonly kind = signal<DocumentKind>('RESUME');

  protected readonly kinds = DOCUMENT_KINDS;
  protected readonly labels = KIND_LABELS;
  protected readonly accept = ACCEPTED_FILES;
  protected readonly formatBytes = formatBytes;

  /** Documents grouped by kind, in the order kinds are listed. */
  protected readonly groups = computed(() => {
    const docs = this.documents() ?? [];
    return DOCUMENT_KINDS.map((kind) => ({
      kind,
      docs: docs.filter((d) => d.kind === kind),
    })).filter((g) => g.docs.length > 0);
  });

  constructor() {
    effect(() => {
      const docs = this.documents();
      if (docs) this.changed.emit(docs);
    });
  }

  ngOnInit(): void {
    this.kind.set(this.defaultKind());
    this.reload();
  }

  reload(): void {
    this.api.list(this.applicationId()).subscribe({
      next: (docs) => this.documents.set(docs),
      error: () => this.error.set('Could not load documents.'),
    });
  }

  protected contentUrl(doc: DocumentResponse): string {
    return this.api.contentUrl(doc);
  }

  protected upload(input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) return;

    this.uploading.set(true);
    this.notice.set(null);
    this.api
      .upload(this.applicationId(), file, this.kind())
      .pipe(finalize(() => this.uploading.set(false)))
      .subscribe({
        next: (doc) => {
          this.documents.update((docs) => [doc, ...(docs ?? [])]);
          input.value = '';
        },
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not upload the file.')),
      });
  }

  protected remove(doc: DocumentResponse): void {
    if (!confirm(`Delete ${doc.filename}?`)) return;

    this.deleting.update((s) => new Set(s).add(doc.id));
    this.api
      .delete(this.applicationId(), doc.id)
      .pipe(
        finalize(() =>
          this.deleting.update((s) => {
            const next = new Set(s);
            next.delete(doc.id);
            return next;
          }),
        ),
      )
      .subscribe({
        next: () => this.documents.update((docs) => (docs ?? []).filter((d) => d.id !== doc.id)),
        error: (err: HttpErrorResponse) => {
          if (err.status === 404) {
            this.documents.update((docs) => (docs ?? []).filter((d) => d.id !== doc.id));
          } else {
            this.notice.set('Could not delete the file. Try again.');
          }
        },
      });
  }

  protected capture(): void {
    const id = this.applicationId();
    if (!id) return;

    this.capturing.set(true);
    this.notice.set(null);
    this.api
      .capturePosting(id)
      .pipe(finalize(() => this.capturing.set(false)))
      .subscribe({
        next: (result) => {
          this.documents.update((docs) => [result.document, ...(docs ?? [])]);
          this.captured.emit(result.descriptionSaved);
        },
        error: (err: HttpErrorResponse) => {
          const reason = problemText(err, 'Could not save the posting.');
          // Some server messages already say what to do instead.
          this.notice.set(
            /upload/i.test(reason)
              ? reason
              : `${reason} You can also open the posting, print it to PDF yourself and upload it here.`,
          );
        },
      });
  }
}

/** The server's explanation when it sent one. */
export function problemText(err: HttpErrorResponse, fallback: string): string {
  const problem = err.error as ProblemDetail | null;
  const fieldError = problem?.errors ? Object.values(problem.errors)[0]?.[0] : undefined;
  return fieldError ?? problem?.detail ?? fallback;
}
