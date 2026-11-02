import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { DocumentsApi } from '../documents/documents-api';
import { problemText } from '../documents/application-documents';
import { ResumesApi } from '../resumes/resumes-api';
import { pickResume, ResumeChoice, resumeChoices } from '../resumes/resume.model';
import { FitApi } from './fit-api';
import { Fit } from './fit.model';
import { FitResult } from './fit-result';

/** How well a resume fits this application's posting, checked with AI on request. */
@Component({
  selector: 'app-application-fit',
  imports: [RouterLink, FitResult],
  templateUrl: './application-fit.html',
})
export class ApplicationFit implements OnInit {
  private readonly api = inject(FitApi);
  private readonly documents = inject(DocumentsApi);
  private readonly libraryResumes = inject(ResumesApi);

  readonly applicationId = input.required<string>();
  readonly hasJobDescription = input(false);

  /** A new check finished; it may have filled the apply-by date. */
  readonly scored = output<Fit>();

  /** Undefined while loading; null when never checked. */
  protected readonly fit = signal<Fit | null | undefined>(undefined);
  protected readonly configured = signal<boolean | null>(null);
  protected readonly open = signal(false);
  protected readonly resumes = signal<ResumeChoice[] | null>(null);
  protected readonly resumeId = signal('');
  protected readonly checking = signal(false);
  protected readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    this.api.forApplication(this.applicationId()).subscribe({
      next: (fit) => {
        this.fit.set(fit);
        if (fit) this.resumeId.set(fit.resumeDocumentId);
      },
      error: () => {
        this.fit.set(null);
        this.notice.set('Could not load the fit.');
      },
    });
    this.api.isConfigured().subscribe({
      next: (configured) => this.configured.set(configured),
      error: () => this.configured.set(false),
    });
  }

  /** Opens the picker and lists resumes fresh, so a just-uploaded one shows up. */
  protected start(): void {
    this.open.set(true);
    this.notice.set(null);
    forkJoin({
      own: this.documents.list(this.applicationId()),
      library: this.libraryResumes.list(),
    }).subscribe({
      next: ({ own, library }) => {
        const resumes = resumeChoices(own, library);
        this.resumes.set(resumes);
        // The resume of the last check, else the default.
        this.resumeId.set(pickResume(resumes, this.resumeId()));
      },
      error: () => this.notice.set('Could not load your resumes.'),
    });
  }

  protected check(): void {
    if (!this.resumeId()) return;

    this.checking.set(true);
    this.notice.set(null);
    this.api
      .scoreApplication(this.applicationId(), this.resumeId())
      .pipe(finalize(() => this.checking.set(false)))
      .subscribe({
        next: (fit) => {
          this.fit.set(fit);
          this.open.set(false);
          this.scored.emit(fit);
        },
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not check the fit. Try again.')),
      });
  }
}
