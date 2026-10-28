import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, map, Observable } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { employerLabel } from '../applications/application.model';
import { PeopleApi } from '../people/people-api';
import { ContactsApi } from './contacts-api';
import {
  CHANNEL_ICONS,
  CHANNEL_LABELS,
  CHANNELS,
  Contact,
  ContactChannel,
  ContactDirection,
  toLocalInput,
} from './contact.model';

interface Choice {
  id: string;
  label: string;
}

/** A body longer than this starts collapsed. */
const LONG_BODY = 280;

/**
 * The outreach log of one person, or of one application: pasted messages, texts and
 * calls. Give it a personId or an applicationId; the form asks for the other side.
 */
@Component({
  selector: 'app-contact-log',
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './contact-log.html',
})
export class ContactLog implements OnInit {
  private readonly api = inject(ContactsApi);
  private readonly applications = inject(ApplicationsApi);
  private readonly people = inject(PeopleApi);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly personId = input<string | null>(null);
  readonly applicationId = input<string | null>(null);
  /** After a contact is saved; one about an application may have put the person on it. */
  readonly logged = output<void>();

  protected readonly contacts = signal<Contact[] | null>(null);
  protected readonly error = signal<string | null>(null);
  /** The contact being edited, 'new' while logging one, or null when the form is closed. */
  protected readonly editing = signal<Contact | 'new' | null>(null);
  protected readonly choices = signal<Choice[]>([]);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  private readonly expanded = signal<ReadonlySet<string>>(new Set());

  protected readonly channels = CHANNELS;
  protected readonly channelLabels = CHANNEL_LABELS;
  protected readonly channelIcons = CHANNEL_ICONS;

  /** True on a person's page: the form picks an application instead of a person. */
  protected readonly forPerson = computed(() => this.personId() != null);

  protected readonly form = this.fb.group({
    /** The application (on a person's page, '' for none) or the person (on an application's page). */
    other: [''],
    channel: ['LINKEDIN' as ContactChannel],
    direction: ['INBOUND' as ContactDirection],
    occurredAt: ['', Validators.required],
    body: ['', Validators.maxLength(20000)],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.list().subscribe({
      next: (contacts) => this.contacts.set(contacts),
      error: () => this.error.set('Could not load the contact log.'),
    });
  }

  /** Opens the form for a new contact; "Log a call" starts it as a call the user made. */
  protected start(
    channel: ContactChannel = 'LINKEDIN',
    direction: ContactDirection = 'INBOUND',
  ): void {
    this.form.reset({
      other: '',
      channel,
      direction,
      occurredAt: toLocalInput(new Date()),
      body: '',
    });
    this.open('new');
  }

  protected edit(contact: Contact): void {
    this.form.reset({
      other: (this.forPerson() ? contact.applicationId : contact.personId) ?? '',
      channel: contact.channel,
      direction: contact.direction,
      occurredAt: toLocalInput(new Date(contact.occurredAt)),
      body: contact.body ?? '',
    });
    this.open(contact);
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected save(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid || (!this.forPerson() && !value.other)) {
      this.form.markAllAsTouched();
      this.formError.set(this.forPerson() ? 'Pick when it happened.' : 'Pick who it was with.');
      return;
    }
    const fields = {
      personId: this.personId() ?? value.other,
      applicationId: this.forPerson() ? value.other || null : this.applicationId(),
      channel: value.channel,
      direction: value.direction,
      occurredAt: new Date(value.occurredAt).toISOString(),
      body: value.body.trim() || null,
    };
    const editing = this.editing();
    const request =
      editing && editing !== 'new'
        ? this.api.update(editing.id, { ...fields, version: editing.version })
        : this.api.create(fields);

    this.saving.set(true);
    this.formError.set(null);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.editing.set(null);
        this.reload();
        this.logged.emit();
      },
      error: (err: HttpErrorResponse) =>
        this.formError.set(
          err.status === 409
            ? 'This contact was changed somewhere else. Close the form and try again.'
            : 'Could not save the contact. Try again.',
        ),
    });
  }

  protected remove(contact: Contact): void {
    if (!confirm('Delete this contact from the log?')) return;
    this.api.delete(contact.id).subscribe({
      next: () => this.contacts.update((list) => (list ?? []).filter((c) => c.id !== contact.id)),
      error: () => this.error.set('Could not delete the contact.'),
    });
  }

  protected isLong(contact: Contact): boolean {
    const body = contact.body ?? '';
    return body.length > LONG_BODY || body.split('\n').length > 4;
  }

  protected isExpanded(contact: Contact): boolean {
    return this.expanded().has(contact.id);
  }

  protected toggle(contact: Contact): void {
    this.expanded.update((ids) => {
      const next = new Set(ids);
      if (!next.delete(contact.id)) next.add(contact.id);
      return next;
    });
  }

  /** "From Pat Lee" when they reached out, "To Pat Lee" when the user did. */
  protected heading(contact: Contact): string {
    return `${contact.direction === 'INBOUND' ? 'From' : 'To'} ${contact.personName}`;
  }

  protected about(contact: Contact): string {
    return `${employerLabel(contact)} – ${contact.jobRole}`;
  }

  private open(target: Contact | 'new'): void {
    this.formError.set(null);
    this.editing.set(target);
    this.loadChoices().subscribe({
      next: (choices) => this.choices.set(choices),
      error: () => this.formError.set('Could not load the list to pick from.'),
    });
  }

  private list(): Observable<Contact[]> {
    const personId = this.personId();
    return personId
      ? this.api.withPerson(personId)
      : this.api.aboutApplication(this.applicationId()!);
  }

  /** Loaded each time the form opens, so people or applications added since show up. */
  private loadChoices(): Observable<Choice[]> {
    return this.forPerson()
      ? this.applications
          .list()
          .pipe(
            map((apps) =>
              apps.map((a) => ({ id: a.id, label: `${employerLabel(a)} – ${a.role}` })),
            ),
          )
      : this.people.list().pipe(
          map((people) =>
            people.map((p) => ({
              id: p.id,
              label: p.company ? `${p.name} (${p.company})` : p.name,
            })),
          ),
        );
  }
}
