import { Component, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { ApplicationResponse } from '../applications/application.model';
import { DocumentsApi } from '../documents/documents-api';
import { DocumentResponse } from '../documents/document.model';
import { problemText } from '../documents/application-documents';
import { PostingView } from './posting-view';

/** A clean, printable copy of the posting built from what Beacon stored. */
@Component({
  selector: 'app-printable-posting',
  imports: [PostingView, RouterLink],
  templateUrl: './printable-posting.html',
  styleUrl: './printable-posting.css',
})
export class PrintablePosting implements OnInit {
  private readonly applications = inject(ApplicationsApi);
  private readonly documents = inject(DocumentsApi);

  /** Bound from the `:id` route param. */
  readonly id = input.required<string>();

  protected readonly application = signal<ApplicationResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal<DocumentResponse | null>(null);
  protected readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    this.applications.getById(this.id()).subscribe({
      next: (app) => this.application.set(app),
      error: (err: HttpErrorResponse) =>
        this.error.set(
          err.status === 404
            ? 'That application no longer exists.'
            : 'Could not load the application. Is the API running?',
        ),
    });
  }

  protected print(): void {
    window.print();
  }

  /** Renders this view on the server and keeps the PDF with the application's documents. */
  protected saveToDocuments(): void {
    this.saving.set(true);
    this.notice.set(null);
    this.documents
      .savePostingSummary(this.id())
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (doc) => this.saved.set(doc),
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not save the PDF. Try again.')),
      });
  }

  protected contentUrl(doc: DocumentResponse): string {
    return this.documents.contentUrl(doc);
  }
}
