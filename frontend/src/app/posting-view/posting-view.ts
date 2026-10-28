import { Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ApplicationResponse, employerLabel } from '../applications/application.model';
import { formatSalary } from '../postings/format';

/**
 * The job ad as Beacon stored it, laid out like a posting. Used in the detail
 * page's side panel and on the printable posting page.
 */
@Component({
  selector: 'app-posting-view',
  imports: [DatePipe],
  templateUrl: './posting-view.html',
  styleUrl: './posting-view.css',
})
export class PostingView {
  readonly application = input.required<ApplicationResponse>();

  protected readonly salary = computed(() => formatSalary(this.application()));
  protected readonly employer = computed(() => employerLabel(this.application()));

  protected readonly workArrangement = computed(() => {
    const remote = this.application().remote;
    return remote === true ? 'Remote' : remote === false ? 'On-site or hybrid' : null;
  });

  /** Paragraphs split on blank lines, so long ads read like the original. */
  protected readonly paragraphs = computed(() =>
    (this.application().jobDescription ?? '')
      .split(/\n\s*\n/)
      .map((p) => p.trim())
      .filter((p) => p.length > 0),
  );
}
