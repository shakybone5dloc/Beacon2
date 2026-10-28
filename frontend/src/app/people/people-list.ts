import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { PeopleApi } from './people-api';
import { PersonForm } from './person-form';
import { Person, PERSON_ROLE_LABELS } from './person.model';

/** Everyone in the user's job search: recruiters, hiring managers, interviewers, referrals. */
@Component({
  selector: 'app-people-list',
  imports: [RouterLink, PersonForm],
  templateUrl: './people-list.html',
  styleUrl: './people.css',
})
export class PeopleList implements OnInit {
  private readonly api = inject(PeopleApi);
  private readonly router = inject(Router);

  protected readonly people = signal<Person[] | null>(null);
  protected readonly error = signal(false);
  protected readonly adding = signal(false);
  protected readonly roleLabels = PERSON_ROLE_LABELS;

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (people) => this.people.set(people),
      error: () => this.error.set(true),
    });
  }

  /** "Senior Recruiter, Robert Half", or whichever of the two is known. */
  protected where(p: Person): string {
    return [p.title, p.company].filter((part) => !!part).join(', ');
  }

  protected added(person: Person): void {
    this.router.navigate(['/people', person.id]);
  }
}
