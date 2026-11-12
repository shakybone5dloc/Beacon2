import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, of, switchMap } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { problemText } from '../documents/application-documents';
import { CheckInApi } from './check-in-api';
import {
  CheckIn,
  composeUrl,
  daysFromNow,
  greet,
  MAIL_CLIENT_LABELS,
  TEAM_SUGGESTIONS,
} from './check-in.model';
import { ContactsApi } from './contacts-api';

/** The "With" choice that means a team, not one of the people. */
export const TEAM = '__team';

/**
 * Starts a check-in email about one application ("is there any update?"): pick who it goes
 * to, edit the filled-in template or have the AI draft it, and open it in the user's own
 * mail to send. Beacon never sends it; afterwards it offers to log it as a contact and set
 * a follow-up date.
 */
@Component({
  selector: 'app-check-in-email',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './check-in-email.html',
})
export class CheckInEmail implements OnInit {
  private readonly api = inject(CheckInApi);
  private readonly contacts = inject(ContactsApi);
  private readonly applications = inject(ApplicationsApi);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly applicationId = input.required<string>();
  /** The email was logged as a contact (and a follow-up may have been set). */
  readonly logged = output<void>();
  readonly closed = output<void>();

  protected readonly checkIn = signal<CheckIn | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly drafting = signal(false);
  /** True once the message was opened in the user's mail, when "Did you send it?" shows. */
  protected readonly opened = signal(false);
  protected readonly logging = signal(false);

  protected readonly team = TEAM;
  protected readonly teamSuggestions = TEAM_SUGGESTIONS;
  protected readonly clientLabels = MAIL_CLIENT_LABELS;

  protected readonly form = this.fb.group({
    /** A person's id, or TEAM. */
    to: [TEAM],
    teamName: ['', Validators.maxLength(100)],
    address: [''],
    subject: [''],
    body: ['', Validators.maxLength(10000)],
    followUp: [true],
    followUpOn: [daysFromNow(7)],
  });

  /** "Open in Gmail" or "Open in my mail app". */
  protected readonly openLabel = computed(() => {
    const client = this.checkIn()?.mailClient;
    return client ? `Open in ${MAIL_CLIENT_LABELS[client]}` : 'Open';
  });

  ngOnInit(): void {
    this.api.start(this.applicationId()).subscribe({
      next: (checkIn) => {
        this.checkIn.set(checkIn);
        // Someone with an address is the likeliest person to write to; otherwise the team.
        const person = checkIn.recipients.find((r) => r.email) ?? null;
        this.form.reset({
          to: person?.personId ?? TEAM,
          teamName: checkIn.defaultTeam,
          address: person?.email ?? '',
          subject: checkIn.subject,
          body: greet(checkIn.body, person?.name ?? null),
          followUp: true,
          followUpOn: daysFromNow(7),
        });
      },
      error: () => this.error.set('Could not start the email. Try again.'),
    });
  }

  /** Picking someone else fills in their address and re-addresses the greeting. */
  protected recipientChanged(): void {
    const person = this.person();
    this.form.patchValue({
      address: person?.email ?? '',
      body: greet(this.form.controls.body.value, person?.name ?? null),
    });
  }

  protected draft(): void {
    this.drafting.set(true);
    this.notice.set(null);
    this.api
      .draft(this.applicationId(), this.recipientName(), this.form.controls.body.value)
      .pipe(finalize(() => this.drafting.set(false)))
      .subscribe({
        next: (draft) => this.form.patchValue({ subject: draft.subject, body: draft.body }),
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not draft the email. Try again.')),
      });
  }

  /** The link the Open button follows; the address may be blank, to fill in in the mail itself. */
  protected url(): string {
    const value = this.form.getRawValue();
    return composeUrl(
      this.checkIn()?.mailClient ?? 'GMAIL',
      value.address.trim(),
      value.subject.trim(),
      value.body,
    );
  }

  protected markOpened(): void {
    this.opened.set(true);
    this.notice.set(null);
  }

  /** Logs the email as a contact with the person or team, then sets the follow-up if asked. */
  protected log(): void {
    const value = this.form.getRawValue();
    const person = this.person();
    const teamName = value.teamName.trim();
    if (!person && !teamName) {
      this.notice.set('Name the team it went to, like "Hiring team".');
      return;
    }
    this.logging.set(true);
    this.notice.set(null);
    this.contacts
      .create({
        personId: person?.personId ?? null,
        team: person ? null : teamName,
        applicationId: this.applicationId(),
        channel: 'EMAIL',
        direction: 'OUTBOUND',
        occurredAt: new Date().toISOString(),
        body: `Subject: ${value.subject.trim()}\n\n${value.body.trim()}`,
      })
      .pipe(
        switchMap(() =>
          value.followUp && value.followUpOn
            ? // The version is read fresh: the page's copy may be older than the last save.
              this.applications
                .getById(this.applicationId())
                .pipe(
                  switchMap((app) =>
                    this.applications.scheduleFollowUp(app.id, value.followUpOn, app.version),
                  ),
                )
            : of(null),
        ),
        finalize(() => this.logging.set(false)),
      )
      .subscribe({
        next: () => this.logged.emit(),
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not log the email. Try again.')),
      });
  }

  protected person() {
    const to = this.form.controls.to.value;
    return this.checkIn()?.recipients.find((r) => r.personId === to) ?? null;
  }

  private recipientName(): string {
    return this.person()?.name ?? this.form.controls.teamName.value.trim();
  }
}
