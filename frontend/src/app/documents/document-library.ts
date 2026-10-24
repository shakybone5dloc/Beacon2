import { Component } from '@angular/core';
import { ApplicationDocuments } from './application-documents';

/** Documents reused across applications, such as a main resume. */
@Component({
  selector: 'app-document-library',
  imports: [ApplicationDocuments],
  template: `
    <h1>Documents</h1>
    <p class="intro">
      Files you reuse across applications, like your main resume. Resumes here can be used to draft
      cover letters for any application. Files for a single application live on that application's
      page.
    </p>
    <app-application-documents [applicationId]="null" defaultKind="RESUME" />
  `,
  styles: `
    :host {
      display: block;
      max-width: 720px;
    }
    .intro {
      color: var(--muted);
    }
  `,
})
export class DocumentLibrary {}
