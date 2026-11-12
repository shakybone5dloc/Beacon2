import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { PeopleApi } from '../people/people-api';
import { StudyApi } from '../study/study-api';
import { Deck } from '../study/study.model';
import { InterviewsApi } from './interviews-api';
import {
  browserTimeZone,
  DURATIONS,
  durationLabel,
  formatInZone,
  Interview,
  Interviewer,
  INTERVIEW_TYPE_LABELS,
  INTERVIEW_TYPES,
  InterviewOutcome,
  InterviewType,
  OUTCOME_LABELS,
  OUTCOMES,
  timeZones,
  toInputValue,
  viewerTime,
} from './interview.model';

/** The interviews on one application: when, what kind, with whom, prep and debrief. */
@Component({
  selector: 'app-application-interviews',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './application-interviews.html',
})
export class ApplicationInterviews implements OnInit {
  private readonly api = inject(InterviewsApi);
  private readonly peopleApi = inject(PeopleApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly studyApi = inject(StudyApi);
  private readonly router = inject(Router);

  readonly applicationId = input.required<string>();
  /** After any change: the board's next interview moves, and interviewers join the application. */
  readonly changed = output<void>();

  protected readonly interviews = signal<Interview[] | null>(null);
  protected readonly error = signal<string | null>(null);
  /** The interview being edited, 'new' while scheduling one, or null when the form is closed. */
  protected readonly editing = signal<Interview | 'new' | null>(null);
  /** Who can be picked: the people on this application, plus anyone already on the interview. */
  protected readonly people = signal<Interviewer[]>([]);
  protected readonly zones = signal<string[]>([]);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly interviewerIds = signal<ReadonlySet<string>>(new Set());
  /** The application's study deck, once it has one (A6). */
  protected readonly deck = signal<Deck | null>(null);
  /** The interview whose study cards are being made. */
  protected readonly makingFor = signal<string | null>(null);
  /** Why making cards failed, under that interview. */
  protected readonly studyNotice = signal<{ interviewId: string; text: string } | null>(null);

  protected readonly types = INTERVIEW_TYPES;
  protected readonly typeLabels = INTERVIEW_TYPE_LABELS;
  protected readonly outcomes = OUTCOMES;
  protected readonly outcomeLabels = OUTCOME_LABELS;
  protected readonly durations = DURATIONS;
  protected readonly durationLabel = durationLabel;
  protected readonly formatInZone = formatInZone;
  protected readonly viewerTime = viewerTime;

  /** The first interview still ahead and not cancelled, to mark as next. */
  protected readonly nextId = computed(() => {
    const now = Date.now();
    return (
      (this.interviews() ?? []).find(
        (i) => new Date(i.startsAt).getTime() > now && i.outcome !== 'CANCELLED',
      )?.id ?? null
    );
  });

  protected readonly form = this.fb.group({
    type: ['PHONE_SCREEN' as InterviewType],
    localStart: ['', Validators.required],
    timeZone: [browserTimeZone(), Validators.required],
    durationMinutes: [60],
    location: ['', Validators.maxLength(2048)],
    prepNotes: ['', Validators.maxLength(10000)],
    debriefNotes: ['', Validators.maxLength(10000)],
    /** '' means not known yet. */
    outcome: ['' as InterviewOutcome | ''],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.api.onApplication(this.applicationId()).subscribe({
      next: (interviews) => this.interviews.set(interviews),
      error: () => this.error.set('Could not load the interviews.'),
    });
    // Without it the interviews still show; only the "Study N cards" link is missing.
    this.studyApi.decks().subscribe({
      next: (decks) => this.deck.set(decks.find((d) => d.applicationId === this.applicationId()) ?? null),
      error: () => this.deck.set(null),
    });
  }

  /** Still ahead and not cancelled: worth studying for. */
  protected isAhead(interview: Interview): boolean {
    return !this.isPast(interview) && interview.outcome !== 'CANCELLED';
  }

  /**
   * Drafts cards from the brief and this interview's prep notes (or the posting and fit gaps)
   * and opens the deck to keep or drop them.
   */
  protected makeCards(interview: Interview): void {
    this.makingFor.set(interview.id);
    this.studyNotice.set(null);
    this.studyApi.fromInterview(interview.id).subscribe({
      next: (made) => {
        this.makingFor.set(null);
        this.router.navigate(['/study', made.deck.id], {
          queryParams: { added: made.added, skipped: made.skipped, from: 'interview' },
        });
      },
      error: (err: HttpErrorResponse) => {
        this.makingFor.set(null);
        this.studyNotice.set({
          interviewId: interview.id,
          text: problemText(err, 'Could not make study cards. Try again.'),
        });
      },
    });
  }

  protected start(): void {
    this.form.reset({
      type: 'PHONE_SCREEN',
      localStart: '',
      timeZone: browserTimeZone(),
      durationMinutes: 60,
      location: '',
      prepNotes: '',
      debriefNotes: '',
      outcome: '',
    });
    this.interviewerIds.set(new Set());
    this.open('new');
  }

  protected edit(interview: Interview): void {
    this.form.reset({
      type: interview.type,
      localStart: toInputValue(interview.localStart),
      timeZone: interview.timeZone,
      durationMinutes: interview.durationMinutes,
      location: interview.location ?? '',
      prepNotes: interview.prepNotes ?? '',
      debriefNotes: interview.debriefNotes ?? '',
      outcome: interview.outcome ?? '',
    });
    this.interviewerIds.set(new Set(interview.interviewers.map((p) => p.id)));
    this.open(interview);
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected toggleInterviewer(id: string, on: boolean): void {
    this.interviewerIds.update((ids) => {
      const next = new Set(ids);
      if (on) next.add(id);
      else next.delete(id);
      return next;
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('Pick the date and time.');
      return;
    }
    const value = this.form.getRawValue();
    const fields = {
      type: value.type,
      localStart: `${value.localStart}:00`,
      timeZone: value.timeZone,
      durationMinutes: Number(value.durationMinutes),
      location: value.location.trim() || null,
      interviewerIds: [...this.interviewerIds()],
      prepNotes: value.prepNotes.trim() || null,
      debriefNotes: value.debriefNotes.trim() || null,
      outcome: value.outcome || null,
    };
    const editing = this.editing();
    const request =
      editing && editing !== 'new'
        ? this.api.update(editing.id, { ...fields, version: editing.version })
        : this.api.schedule(this.applicationId(), fields);

    this.saving.set(true);
    this.formError.set(null);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.editing.set(null);
        this.reload();
        this.changed.emit();
      },
      error: (err: HttpErrorResponse) =>
        this.formError.set(
          err.status === 409
            ? 'This interview was changed somewhere else. Close the form and try again.'
            : 'Could not save the interview. Try again.',
        ),
    });
  }

  protected remove(interview: Interview): void {
    if (!confirm(`Delete the ${INTERVIEW_TYPE_LABELS[interview.type].toLowerCase()} interview?`)) {
      return;
    }
    this.api.delete(interview.id).subscribe({
      next: () => {
        this.interviews.update((list) => (list ?? []).filter((i) => i.id !== interview.id));
        this.changed.emit();
      },
      error: () => this.error.set('Could not delete the interview.'),
    });
  }

  protected calendarUrl(interview: Interview): string {
    return this.api.calendarUrl(interview);
  }

  protected isPast(interview: Interview): boolean {
    return new Date(interview.endsAt).getTime() < Date.now();
  }

  protected isLink(location: string): boolean {
    return /^https?:\/\/\S+$/i.test(location);
  }

  /**
   * Loaded each time the form opens, so people added to the application since show up.
   * An interviewer since taken off the application stays listed on their interview.
   */
  private open(target: Interview | 'new'): void {
    this.formError.set(null);
    this.editing.set(target);
    this.zones.set(timeZones(this.form.controls.timeZone.value));
    this.loadPeople(target);
  }

  /** People were added to or taken off the application while the form may be open. */
  refreshPeople(): void {
    const target = this.editing();
    if (target) this.loadPeople(target);
  }

  private loadPeople(target: Interview | 'new'): void {
    this.peopleApi.onApplication(this.applicationId()).subscribe({
      next: (linked) => {
        const people: Interviewer[] = linked.map(({ person }) => ({
          id: person.id,
          name: person.name,
          company: person.company,
        }));
        const already = target === 'new' ? [] : target.interviewers;
        const missing = already.filter((i) => !people.some((p) => p.id === i.id));
        this.people.set([...people, ...missing].sort((a, b) => a.name.localeCompare(b.name)));
      },
      error: () => this.formError.set('Could not load the people to pick interviewers from.'),
    });
  }
}
