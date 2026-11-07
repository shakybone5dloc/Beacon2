import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { DocumentsApi } from '../documents/documents-api';
import { problemText } from '../documents/application-documents';
import { ResumesApi } from '../resumes/resumes-api';
import { pickResume, ResumeChoice, resumeChoices } from '../resumes/resume.model';
import { TailoringApi } from './tailoring-api';
import {
  KIND_LABELS,
  ResumeSuggestion,
  splitSuggestions,
  SuggestionState,
  Tailoring,
} from './tailoring.model';

/**
 * The application's resume (A2): which one was sent with it, and AI suggestions for
 * tailoring one to the posting, each marked done or dismissed as the user works through them.
 */
@Component({
  selector: 'app-application-resume',
  imports: [RouterLink],
  templateUrl: './application-resume.html',
})
export class ApplicationResume implements OnInit {
  private readonly api = inject(TailoringApi);
  private readonly documents = inject(DocumentsApi);
  private readonly libraryResumes = inject(ResumesApi);

  readonly applicationId = input.required<string>();
  readonly hasJobDescription = input(false);

  protected readonly tailoring = signal<Tailoring | null>(null);
  protected readonly resumes = signal<ResumeChoice[] | null>(null);
  /** The resume to tailor; the one the newest suggestions are for, else the default. */
  protected readonly resumeId = signal('');
  protected readonly suggesting = signal(false);
  protected readonly savingSent = signal(false);
  protected readonly showDismissed = signal(false);
  protected readonly copied = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly kindLabels = KIND_LABELS;
  protected readonly groups = computed(() => splitSuggestions(this.tailoring()?.suggestions ?? []));
  protected readonly openCount = computed(
    () => this.groups().active.filter((s) => s.state === 'OPEN').length,
  );

  ngOnInit(): void {
    forkJoin({
      tailoring: this.api.get(this.applicationId()),
      own: this.documents.list(this.applicationId()),
      library: this.libraryResumes.list(),
    }).subscribe({
      next: ({ tailoring, own, library }) => {
        const choices = resumeChoices(own, library);
        this.resumes.set(choices);
        this.tailoring.set(tailoring);
        this.resumeId.set(pickResume(choices, tailoring.resumeDocumentId ?? ''));
      },
      error: () => this.notice.set('Could not load the resume section.'),
    });
  }

  protected setSent(value: string): void {
    this.savingSent.set(true);
    this.notice.set(null);
    this.api
      .setSent(this.applicationId(), value === '' ? null : value)
      .pipe(finalize(() => this.savingSent.set(false)))
      .subscribe({
        next: (t) => this.tailoring.set(t),
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not save which resume was sent.')),
      });
  }

  protected suggest(): void {
    this.suggesting.set(true);
    this.notice.set(null);
    this.api
      .suggest(this.applicationId(), this.resumeId() || null)
      .pipe(finalize(() => this.suggesting.set(false)))
      .subscribe({
        next: (t) => {
          this.tailoring.set(t);
          if (t.resumeDocumentId) this.resumeId.set(t.resumeDocumentId);
        },
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not get suggestions. Try again.')),
      });
  }

  protected mark(suggestion: ResumeSuggestion, state: SuggestionState): void {
    this.notice.set(null);
    this.api.mark(this.applicationId(), suggestion.id, state).subscribe({
      next: (updated) =>
        this.tailoring.update((t) =>
          t
            ? { ...t, suggestions: t.suggestions.map((s) => (s.id === updated.id ? updated : s)) }
            : t,
        ),
      error: (err: HttpErrorResponse) =>
        this.notice.set(problemText(err, 'Could not update that suggestion.')),
    });
  }

  protected copy(suggestion: ResumeSuggestion): void {
    if (!suggestion.example) return;
    navigator.clipboard?.writeText(suggestion.example);
    this.copied.set(suggestion.id);
    setTimeout(() => this.copied.update((id) => (id === suggestion.id ? null : id)), 1500);
  }
}
