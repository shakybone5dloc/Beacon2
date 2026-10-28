import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, finalize, of, switchMap } from 'rxjs';
import { ProblemDetail } from '../applications/application.model';
import { CompaniesApi } from '../companies/companies-api';
import { problemText } from '../documents/application-documents';
import { PeopleApi } from './people-api';
import {
  Person,
  PERSON_ROLE_LABELS,
  PERSON_ROLES,
  PersonRole,
  RECRUITER_TYPE_LABELS,
  RECRUITER_TYPES,
  RecruiterType,
} from './person.model';

type FieldName = 'name' | 'company' | 'title' | 'email' | 'phone' | 'linkedinUrl' | 'notes';

/** Creates a person, or edits the one given. Emits the saved person. */
@Component({
  selector: 'app-person-form',
  imports: [ReactiveFormsModule],
  templateUrl: './person-form.html',
  styleUrl: './person-form.css',
})
export class PersonForm implements OnInit {
  private readonly people = inject(PeopleApi);
  private readonly companies = inject(CompaniesApi);
  private readonly fb = inject(NonNullableFormBuilder);

  /** The person to edit; absent when creating. */
  readonly person = input<Person | null>(null);
  /** The usual role a new person starts with. */
  readonly defaultRole = input<PersonRole>('RECRUITER');
  readonly saved = output<Person>();
  readonly cancelled = output<void>();

  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly roles = PERSON_ROLES;
  protected readonly roleLabels = PERSON_ROLE_LABELS;
  protected readonly recruiterTypes = RECRUITER_TYPES;
  protected readonly recruiterTypeLabels = RECRUITER_TYPE_LABELS;

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    company: ['', [Validators.maxLength(200)]],
    title: ['', [Validators.maxLength(200)]],
    role: ['RECRUITER' as PersonRole],
    /** '' means not specified. */
    recruiterType: ['' as RecruiterType | ''],
    email: ['', [Validators.email, Validators.maxLength(320)]],
    phone: ['', [Validators.maxLength(50)]],
    linkedinUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^https?:\/\/.+/)]],
    notes: ['', [Validators.maxLength(4000)]],
  });

  protected readonly role = toSignal(this.form.controls.role.valueChanges, {
    initialValue: this.form.controls.role.value,
  });

  protected readonly companySuggestions = toSignal(
    this.form.controls.company.valueChanges.pipe(
      debounceTime(200),
      distinctUntilChanged(),
      switchMap((text) => (text.trim() ? this.companies.list(text) : of([]))),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );

  ngOnInit(): void {
    const p = this.person();
    this.form.reset({
      name: p?.name ?? '',
      company: p?.company ?? '',
      title: p?.title ?? '',
      role: p?.role ?? this.defaultRole(),
      recruiterType: p?.recruiterType ?? '',
      email: p?.email ?? '',
      phone: p?.phone ?? '',
      linkedinUrl: p?.linkedinUrl ?? '',
      notes: p?.notes ?? '',
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const fields = {
      name: v.name.trim(),
      // A name links to the user's company of that name, or creates it.
      companyName: v.company.trim() || null,
      title: v.title.trim() || null,
      role: v.role,
      recruiterType: v.role === 'RECRUITER' && v.recruiterType ? v.recruiterType : null,
      email: v.email.trim() || null,
      phone: v.phone.trim() || null,
      linkedinUrl: v.linkedinUrl.trim() || null,
      notes: v.notes.trim() || null,
    };
    const existing = this.person();
    const save$ = existing
      ? this.people.update(existing.id, { ...fields, version: existing.version })
      : this.people.create(fields);

    this.saving.set(true);
    this.formError.set(null);
    save$.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (person) => this.saved.emit(person),
      error: (err: HttpErrorResponse) => this.applyServerErrors(err),
    });
  }

  protected errorFor(name: FieldName): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;
    const e = control.errors;
    if (e['server']) return e['server'];
    if (e['required']) return 'Required.';
    if (e['email']) return 'Enter an email address.';
    if (e['maxlength']) return `Must be at most ${e['maxlength'].requiredLength} characters.`;
    if (e['pattern']) return 'Must start with http:// or https://';
    return 'Invalid.';
  }

  private applyServerErrors(err: HttpErrorResponse): void {
    const problem = err.error as ProblemDetail | null;
    if (err.status === 400 && problem?.errors) {
      for (const [field, messages] of Object.entries(problem.errors)) {
        const name = field === 'companyName' || field === 'companyId' ? 'company' : field;
        const control = this.form.get(name);
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
        'This person was changed somewhere else. Reload the page to see the latest.',
      );
      return;
    }
    this.formError.set(problemText(err, 'Could not save.'));
  }
}
