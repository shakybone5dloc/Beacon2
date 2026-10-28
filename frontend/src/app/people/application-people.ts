import { Component, computed, inject, input, OnInit, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { PeopleApi } from './people-api';
import { PersonForm } from './person-form';
import {
  ApplicationPerson,
  Person,
  PERSON_ROLE_LABELS,
  PERSON_ROLES,
  PersonRole,
} from './person.model';

/** Who is involved in one application, and as what. */
@Component({
  selector: 'app-application-people',
  imports: [RouterLink, PersonForm],
  templateUrl: './application-people.html',
  styleUrl: './application-people.css',
})
export class ApplicationPeople implements OnInit {
  private readonly api = inject(PeopleApi);

  readonly applicationId = input.required<string>();
  /** After anyone is added, removed or given another role (a recruiter's agency can change how the employer reads). */
  readonly changed = output<void>();

  protected readonly people = signal<ApplicationPerson[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly roles = PERSON_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  /** What's typed in the "add someone" box. */
  protected readonly search = signal('');
  /** '' means their usual role. */
  protected readonly addRole = signal<PersonRole | ''>('');

  private readonly suggestions = toSignal(
    toObservable(this.search).pipe(
      // A picked suggestion reads "Name (Company)"; search by the name so it stays in the list.
      map((text) => text.replace(/\s*\([^)]*\)\s*$/, '').trim()),
      debounceTime(200),
      distinctUntilChanged(),
      switchMap((name) => (name ? this.api.list(name) : of([]))),
      catchError(() => of([] as Person[])),
    ),
    { initialValue: [] as Person[] },
  );

  /** Suggestions not already on the application, labelled so namesakes can be told apart. */
  protected readonly options = computed(() => {
    const onIt = new Set((this.people() ?? []).map((p) => p.person.id));
    return this.suggestions()
      .filter((p) => !onIt.has(p.id))
      .map((p) => ({ person: p, label: label(p) }));
  });

  /** The suggestion whose label matches what's typed, if any. */
  protected readonly picked = computed(
    () => this.options().find((o) => o.label === this.search().trim())?.person ?? null,
  );

  ngOnInit(): void {
    this.reload();
  }

  /** Logging a contact about the application can put someone on it. */
  reload(): void {
    this.api.onApplication(this.applicationId()).subscribe({
      next: (people) => this.people.set(people),
      error: () => this.error.set('Could not load the people on this application.'),
    });
  }

  protected add(): void {
    const person = this.picked();
    if (!person) return;
    this.linkPerson(person, this.addRole() || null);
    this.search.set('');
    this.addRole.set('');
  }

  /** A person just created here goes straight onto the application. */
  protected created(person: Person): void {
    this.creating.set(false);
    this.linkPerson(person, null);
  }

  protected changeRole(entry: ApplicationPerson, role: PersonRole): void {
    this.api.link(this.applicationId(), entry.person.id, role).subscribe({
      next: (updated) => this.replace(updated),
      error: () => this.error.set('Could not change the role.'),
    });
  }

  protected remove(entry: ApplicationPerson): void {
    this.api.unlink(this.applicationId(), entry.person.id).subscribe({
      next: () => {
        this.people.update((list) => (list ?? []).filter((p) => p.person.id !== entry.person.id));
        this.changed.emit();
      },
      error: () => this.error.set(`Could not remove ${entry.person.name}.`),
    });
  }

  protected contact(person: Person): string {
    return [person.title, person.company, person.email, person.phone]
      .filter((p) => !!p)
      .join(' · ');
  }

  private linkPerson(person: Person, role: PersonRole | null): void {
    this.error.set(null);
    this.api.link(this.applicationId(), person.id, role).subscribe({
      next: (linked) => this.replace(linked),
      error: () => this.error.set(`Could not add ${person.name}.`),
    });
  }

  private replace(entry: ApplicationPerson): void {
    this.people.update((list) => {
      const current = list ?? [];
      return current.some((p) => p.person.id === entry.person.id)
        ? current.map((p) => (p.person.id === entry.person.id ? entry : p))
        : [...current, entry];
    });
    this.changed.emit();
  }
}

function label(p: Person): string {
  return p.company ? `${p.name} (${p.company})` : p.name;
}
