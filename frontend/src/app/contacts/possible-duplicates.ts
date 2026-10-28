import { Component, inject, input, OnInit, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ContactsApi } from './contacts-api';
import { PossibleDuplicate } from './contact.model';

/**
 * Warns when another application looks like the same job (same company and title,
 * added within a few weeks): a second recruiter may be pitching a role the user was
 * already submitted for. Shows nothing otherwise, or if the check fails.
 */
@Component({
  selector: 'app-possible-duplicates',
  imports: [DatePipe, RouterLink, TitleCasePipe],
  template: `
    @if (duplicates().length > 0) {
      <div class="notice" role="note">
        <p class="m-0 flex items-center gap-space-xs font-semibold">
          <span class="material-symbols-outlined !text-base" aria-hidden="true">content_copy</span>
          This looks like the same job as:
        </p>
        <ul class="my-space-xs">
          @for (d of duplicates(); track d.applicationId) {
            <li>
              <a [routerLink]="['/applications', d.applicationId]"
                >{{ d.company }} – {{ d.jobRole }}</a
              >
              ({{ d.status | titlecase }}, added {{ d.createdAt | date: 'mediumDate'
              }}{{ d.recruiters.length > 0 ? ', via ' + d.recruiters.join(', ') : '' }})
            </li>
          }
        </ul>
        <p class="m-0 text-body-sm">
          Being submitted twice for one job can get you disqualified. Check before agreeing to a
          second submission.
        </p>
      </div>
    }
  `,
})
export class PossibleDuplicates implements OnInit {
  private readonly api = inject(ContactsApi);

  readonly applicationId = input.required<string>();

  protected readonly duplicates = signal<PossibleDuplicate[]>([]);

  ngOnInit(): void {
    this.api.possibleDuplicates(this.applicationId()).subscribe({
      next: (duplicates) => this.duplicates.set(duplicates),
      // A warning that can't load just isn't shown; the page works without it.
      error: () => this.duplicates.set([]),
    });
  }
}
