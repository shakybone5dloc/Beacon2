import { Component, inject, input, OnInit, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { ApplicationResponse, ProblemDetail } from '../applications/application.model';
import { problemText } from '../documents/application-documents';
import { PeopleApi } from '../people/people-api';
import { Person, PERSON_ROLE_LABELS } from '../people/person.model';
import { CompaniesApi } from './companies-api';
import { Company, COMPANY_TYPE_LABELS, COMPANY_TYPES, CompanyType } from './company.model';

type FieldName = 'name' | 'website' | 'emailDomains' | 'notes';

/** One company: its details, which can be edited, and every application with it. */
@Component({
  selector: 'app-company-detail',
  imports: [ReactiveFormsModule, RouterLink, TitleCasePipe],
  templateUrl: './company-detail.html',
  styleUrl: './companies.css',
})
export class CompanyDetail implements OnInit {
  private readonly api = inject(CompaniesApi);
  private readonly peopleApi = inject(PeopleApi);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Bound from the `:id` route param. */
  readonly id = input.required<string>();

  protected readonly company = signal<Company | null>(null);
  protected readonly applications = signal<ApplicationResponse[]>([]);
  protected readonly people = signal<Person[]>([]);
  protected readonly roleLabels = PERSON_ROLE_LABELS;
  protected readonly loadError = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly types = COMPANY_TYPES;
  protected readonly typeLabels = COMPANY_TYPE_LABELS;

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    type: ['EMPLOYER' as CompanyType],
    website: ['', [Validators.maxLength(2048), Validators.pattern(/^https?:\/\/.+/)]],
    /** Comma- or space-separated, e.g. "acme.com, acmejobs.com". */
    emailDomains: [''],
    notes: ['', [Validators.maxLength(4000)]],
  });

  ngOnInit(): void {
    forkJoin({
      company: this.api.getById(this.id()),
      applications: this.api.applications(this.id()),
      people: this.peopleApi.atCompany(this.id()),
    }).subscribe({
      next: ({ company, applications, people }) => {
        this.company.set(company);
        this.applications.set(applications);
        this.people.set(people);
      },
      error: (err: HttpErrorResponse) =>
        this.loadError.set(
          err.status === 404 ? 'That company was not found.' : 'Could not load the company.',
        ),
    });
  }

  protected startEdit(): void {
    const c = this.company()!;
    this.form.reset({
      name: c.name,
      type: c.type,
      website: c.website ?? '',
      emailDomains: c.emailDomains.join(', '),
      notes: c.notes ?? '',
    });
    this.formError.set(null);
    this.editing.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);

    this.api
      .update(this.id(), {
        name: v.name.trim(),
        type: v.type,
        website: v.website.trim() || null,
        emailDomains: v.emailDomains.split(/[\s,]+/).filter((d) => d.length > 0),
        notes: v.notes.trim() || null,
        version: this.company()!.version,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (company) => {
          this.company.set(company);
          // Applications show the company's name, so reload them after a rename.
          this.api.applications(this.id()).subscribe((apps) => this.applications.set(apps));
          this.editing.set(false);
        },
        error: (err: HttpErrorResponse) => this.applyServerErrors(err),
      });
  }

  protected errorFor(name: FieldName): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;
    const e = control.errors;
    if (e['server']) return e['server'];
    if (e['required']) return 'Required.';
    if (e['maxlength']) return `Must be at most ${e['maxlength'].requiredLength} characters.`;
    if (e['pattern']) return 'Must start with http:// or https://';
    return 'Invalid.';
  }

  private applyServerErrors(err: HttpErrorResponse): void {
    const problem = err.error as ProblemDetail | null;
    if (err.status === 400 && problem?.errors) {
      for (const [field, messages] of Object.entries(problem.errors)) {
        // List entries come back as emailDomains[0] and so on.
        const control = this.form.get(field.replace(/\[\d+\]$/, ''));
        if (control) {
          control.setErrors({ server: messages[0] });
          control.markAsTouched();
        } else {
          this.formError.set(messages[0]);
        }
      }
      return;
    }
    if (err.status === 409) {
      this.formError.set(
        'This company was changed somewhere else. Reload the page to see the latest.',
      );
      return;
    }
    this.formError.set(problemText(err, 'Could not save the company.'));
  }
}
