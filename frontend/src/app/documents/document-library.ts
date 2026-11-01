import { Component, viewChild } from '@angular/core';
import { ResumeList } from '../resumes/resume-list';
import { ApplicationDocuments } from './application-documents';

/** Documents reused across applications: labelled resumes, and the files themselves. */
@Component({
  selector: 'app-document-library',
  imports: [ApplicationDocuments, ResumeList],
  template: `
    <h1>Documents</h1>
    <p class="intro">
      Files you reuse across applications, like your resumes. Files for a single application live on
      that application's page.
    </p>
    <app-resume-list class="mb-space-xl block" />
    <h2>Files</h2>
    <app-application-documents
      [applicationId]="null"
      defaultKind="RESUME"
      (changed)="resumes()?.reload()"
    />
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
export class DocumentLibrary {
  /** Uploading or deleting a resume changes the list above. */
  protected readonly resumes = viewChild(ResumeList);
}
