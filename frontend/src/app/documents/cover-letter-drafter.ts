import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, forkJoin } from 'rxjs';
import { CoverLettersApi } from './cover-letters-api';
import { DocumentsApi } from './documents-api';
import { DocumentResponse } from './document.model';
import { problemText } from './application-documents';

/** Drafts a cover letter with AI from the job description and a chosen resume, then saves it as a PDF. */
@Component({
  selector: 'app-cover-letter-drafter',
  templateUrl: './cover-letter-drafter.html',
  styleUrl: './cover-letter-drafter.css',
})
export class CoverLetterDrafter implements OnInit {
  private readonly coverLetters = inject(CoverLettersApi);
  private readonly documents = inject(DocumentsApi);

  readonly applicationId = input.required<string>();
  readonly hasJobDescription = input(false);

  readonly saved = output<DocumentResponse>();

  protected readonly configured = signal<boolean | null>(null);
  protected readonly open = signal(false);
  /** This application's resumes first, then the library's. */
  protected readonly resumes = signal<DocumentResponse[] | null>(null);
  protected readonly resumeId = signal('');
  protected readonly instructions = signal('');
  protected readonly draft = signal<string | null>(null);
  protected readonly drafting = signal(false);
  protected readonly saving = signal(false);
  protected readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    this.coverLetters.isConfigured().subscribe({
      next: (configured) => this.configured.set(configured),
      error: () => this.configured.set(false),
    });
  }

  /** Opens the panel and lists resumes fresh, so a just-uploaded one shows up. */
  protected start(): void {
    this.open.set(true);
    this.notice.set(null);
    forkJoin({
      own: this.documents.list(this.applicationId()),
      library: this.documents.list(null),
    }).subscribe({
      next: ({ own, library }) => {
        const resumes = [...own, ...library].filter((d) => d.kind === 'RESUME');
        this.resumes.set(resumes);
        if (!resumes.some((r) => r.id === this.resumeId())) {
          this.resumeId.set(resumes[0]?.id ?? '');
        }
      },
      error: () => this.notice.set('Could not load your resumes.'),
    });
  }

  protected generate(): void {
    if (!this.resumeId()) return;

    this.drafting.set(true);
    this.notice.set(null);
    this.coverLetters
      .draft(this.applicationId(), this.resumeId(), this.instructions().trim() || null)
      .pipe(finalize(() => this.drafting.set(false)))
      .subscribe({
        next: (text) => this.draft.set(text),
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not draft the letter. Try again.')),
      });
  }

  protected save(): void {
    const text = this.draft()?.trim();
    if (!text) return;

    this.saving.set(true);
    this.notice.set(null);
    this.coverLetters
      .save(this.applicationId(), text)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (doc) => {
          this.saved.emit(doc);
          this.draft.set(null);
          this.open.set(false);
        },
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not save the letter. Try again.')),
      });
  }

  protected copy(): void {
    const text = this.draft();
    if (text) navigator.clipboard?.writeText(text);
  }

  protected label(doc: DocumentResponse): string {
    return doc.applicationId ? `${doc.filename} (this application)` : `${doc.filename} (library)`;
  }
}
