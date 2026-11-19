import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  ApplicationStatus,
  employerLabel,
} from '../applications/application.model';
import { ARCHIVE_AFTER_DAYS, isArchived } from '../applications/archive';
import { matchesWords, searchWords } from '../applications/search';

const OUTCOME_PILLS: Partial<Record<ApplicationStatus, string>> = {
  ACCEPTED: 'pill-green',
  REJECTED: 'pill-red',
};

/** Closed applications that have aged off the board, most recently closed first. */
@Component({
  selector: 'app-archive-page',
  imports: [DatePipe, RouterLink, TitleCasePipe],
  templateUrl: './archive-page.html',
  host: { class: 'block max-w-4xl' },
})
export class ArchivePage implements OnInit {
  private readonly api = inject(ApplicationsApi);

  protected readonly archiveAfterDays = ARCHIVE_AFTER_DAYS;
  protected readonly employerLabel = employerLabel;
  protected readonly applications = signal<ApplicationResponse[] | null>(null);
  protected readonly failed = signal(false);
  protected readonly search = signal('');

  private readonly archived = computed(() => {
    const now = new Date();
    return (this.applications() ?? [])
      .filter((app) => isArchived(app, now))
      .sort((a, b) => b.statusChangedAt.localeCompare(a.statusChangedAt));
  });

  protected readonly total = computed(() => this.archived().length);

  protected readonly shown = computed(() => {
    const words = searchWords(this.search());
    return this.archived().filter((app) => matchesWords(app, words));
  });

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (apps) => this.applications.set(apps),
      error: () => this.failed.set(true),
    });
  }

  protected outcomePill(status: ApplicationStatus): string {
    return OUTCOME_PILLS[status] ?? '';
  }
}
