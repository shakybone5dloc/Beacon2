import { Component, inject, input, OnInit, signal } from '@angular/core';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BeaconApi } from '../api/beacon-api';
import { getApplicationTimeline } from '../api/functions';
import * as api from '../api/models';
import { CHANNEL_ICONS, CHANNEL_LABELS } from '../contacts/contact.model';
import { DocumentsApi } from '../documents/documents-api';
import { DocumentResponse, KIND_LABELS } from '../documents/document.model';
import { INTERVIEW_TYPE_LABELS, OUTCOME_LABELS } from '../interviews/interview.model';
import { PERSON_ROLE_LABELS } from '../people/person.model';

export type TimelineEntry = api.TimelineEntryResponse;

/** A message longer than this shows cut short; the full text is in the contact log. */
const SNIPPET = 160;

const KIND_ICONS: Record<TimelineEntry['kind'], string> = {
  STATUS_CHANGE: 'flag',
  PERSON_ADDED: 'person_add',
  CONTACT: 'chat_bubble',
  INTERVIEW: 'event',
  DOCUMENT: 'description',
};

/**
 * Everything that happened on one application, newest first: status changes, people
 * added, contacts, interviews and documents, from one API call. Interviews still
 * ahead sit at the top, marked as upcoming.
 */
@Component({
  selector: 'app-application-timeline',
  imports: [DatePipe, RouterLink, TitleCasePipe],
  templateUrl: './application-timeline.html',
})
export class ApplicationTimeline implements OnInit {
  private readonly api = inject(BeaconApi);
  private readonly documents = inject(DocumentsApi);

  readonly applicationId = input.required<string>();

  protected readonly entries = signal<TimelineEntry[] | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly channelLabels = CHANNEL_LABELS;
  protected readonly kindLabels = KIND_LABELS;
  protected readonly interviewLabels = INTERVIEW_TYPE_LABELS;
  protected readonly outcomeLabels = OUTCOME_LABELS;
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  ngOnInit(): void {
    this.reload();
  }

  /** The page calls this after anything that adds to the timeline. */
  reload(): void {
    this.api.invoke(getApplicationTimeline, { id: this.applicationId() }).subscribe({
      next: (entries) => {
        this.error.set(null);
        this.entries.set(entries);
      },
      error: () => this.error.set('Could not load the timeline.'),
    });
  }

  /** Material Symbols name for the entry's dot. */
  protected icon(entry: TimelineEntry): string {
    if (entry.contact) return CHANNEL_ICONS[entry.contact.channel];
    return KIND_ICONS[entry.kind];
  }

  protected isAhead(entry: TimelineEntry): boolean {
    return new Date(entry.at).getTime() > Date.now();
  }

  protected snippet(text: string): string {
    const flat = text.replace(/\s+/g, ' ').trim();
    return flat.length > SNIPPET ? `${flat.slice(0, SNIPPET).trimEnd()}…` : flat;
  }

  protected documentUrl(document: DocumentResponse): string {
    return this.documents.contentUrl(document);
  }
}
