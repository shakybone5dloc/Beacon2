import { Component, inject, input, OnInit, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { employerLabel } from '../applications/application.model';
import { PeopleApi } from './people-api';
import { PersonForm } from './person-form';
import {
  Person,
  PersonApplication,
  PERSON_ROLE_LABELS,
  RECRUITER_TYPE_LABELS,
} from './person.model';

/** One person: their details, which can be edited, and the applications they're on. */
@Component({
  selector: 'app-person-detail',
  imports: [RouterLink, TitleCasePipe, PersonForm],
  templateUrl: './person-detail.html',
  styleUrl: './people.css',
  host: { class: 'max-w-[720px]' },
})
export class PersonDetail implements OnInit {
  private readonly api = inject(PeopleApi);
  private readonly router = inject(Router);

  /** Bound from the `:id` route param. */
  readonly id = input.required<string>();

  protected readonly person = signal<Person | null>(null);
  protected readonly applications = signal<PersonApplication[]>([]);
  protected readonly loadError = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  protected readonly roleLabels = PERSON_ROLE_LABELS;
  protected readonly recruiterTypeLabels = RECRUITER_TYPE_LABELS;
  protected readonly employerLabel = employerLabel;

  ngOnInit(): void {
    forkJoin({
      person: this.api.getById(this.id()),
      applications: this.api.applicationsOf(this.id()),
    }).subscribe({
      next: ({ person, applications }) => {
        this.person.set(person);
        this.applications.set(applications);
      },
      error: (err: HttpErrorResponse) =>
        this.loadError.set(
          err.status === 404 ? 'That person was not found.' : 'Could not load the person.',
        ),
    });
  }

  protected saved(person: Person): void {
    this.person.set(person);
    this.editing.set(false);
  }

  protected remove(): void {
    const p = this.person()!;
    if (!confirm(`Delete ${p.name}? They'll be taken off every application they're on.`)) return;
    this.api.delete(p.id).subscribe({
      next: () => this.router.navigate(['/people']),
      error: () => this.deleteError.set('Could not delete. Try again in a moment.'),
    });
  }
}
