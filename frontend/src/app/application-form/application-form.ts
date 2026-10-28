import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, of, switchMap } from 'rxjs';
import { ApplicationsApi } from '../applications/applications-api';
import { CompaniesApi } from '../companies/companies-api';
import {
  ApplicationResponse,
  CreateApplicationRequest,
  EMPLOYMENT_TYPE_LABELS,
  EMPLOYMENT_TYPES,
  EmploymentType,
  ProblemDetail,
  SALARY_PERIODS,
} from '../applications/application.model';

type FieldName =
  | 'company'
  | 'role'
  | 'jobUrl'
  | 'notes'
  | 'applyBy'
  | 'followUpOn'
  | 'location'
  | 'salaryMin'
  | 'salaryMax'
  | 'salaryCurrency'
  | 'salaryText'
  | 'hourlyRate'
  | 'source'
  | 'jobDescription';

type PayType = 'salary' | 'hourly';

/** Fields that only apply to one pay type; the other type's are disabled. */
const SALARY_ONLY = ['salaryMin', 'salaryMax', 'salaryPeriod'] as const;

/** Messages for pattern failures, by field. */
const PATTERN_MESSAGES: Partial<Record<FieldName, string>> = {
  jobUrl: 'Must start with http:// or https://',
  salaryMin: 'Whole number, no commas.',
  salaryMax: 'Whole number, no commas.',
  hourlyRate: 'Whole number, no commas.',
  salaryCurrency: 'A 3-letter code, like USD.',
};

@Component({
  selector: 'app-application-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './application-form.html',
  styleUrl: './application-form.css',
})
export class ApplicationForm implements OnInit {
  private readonly api = inject(ApplicationsApi);
  private readonly companies = inject(CompaniesApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Bound from the `:id` route param; absent when creating. */
  readonly id = input<string>();

  protected readonly isEdit = computed(() => this.id() != null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly stale = signal(false);
  /** The posting details section starts open when editing an application that has some. */
  protected readonly detailsOpen = signal(false);
  /** Hourly pay has its own switch, so the salary periods leave "hour" out. */
  protected readonly salaryPeriods = SALARY_PERIODS.filter((p) => p !== 'hour');
  protected readonly employmentTypes = EMPLOYMENT_TYPES;
  protected readonly employmentTypeLabels = EMPLOYMENT_TYPE_LABELS;

  private version = 0;

  protected readonly form = this.fb.group({
    /** Disabled, so neither required nor sent, while the employer isn't disclosed. */
    company: ['', [Validators.required, Validators.maxLength(200)]],
    undisclosed: [false],
    role: ['', [Validators.required, Validators.maxLength(200)]],
    /** '' means not specified. */
    employmentType: ['' as EmploymentType | ''],
    jobUrl: ['', [Validators.maxLength(2048), Validators.pattern(/^https?:\/\/.+/)]],
    notes: ['', [Validators.maxLength(4000)]],
    applyBy: [''],
    followUpOn: [''],
    location: ['', [Validators.maxLength(500)]],
    /** '' not specified, 'yes' remote, 'no' on-site or hybrid. */
    remote: [''],
    salaryMin: ['', [Validators.pattern(/^\d+$/)]],
    salaryMax: ['', [Validators.pattern(/^\d+$/)]],
    salaryCurrency: ['', [Validators.pattern(/^[A-Za-z]{3}$/)]],
    salaryPeriod: ['year'],
    /** Hourly pay is one amount, stored as salaryMin with the period "hour". */
    payType: ['salary' as PayType],
    hourlyRate: ['', [Validators.pattern(/^\d+$/)]],
    salaryText: ['', [Validators.maxLength(100)]],
    source: ['', [Validators.maxLength(100)]],
    postedOn: [''],
    jobDescription: ['', [Validators.maxLength(100_000)]],
  });

  /** The user's companies matching what's typed, offered as the company input's suggestions. */
  protected readonly companySuggestions = toSignal(
    this.form.controls.company.valueChanges.pipe(
      debounceTime(200),
      distinctUntilChanged(),
      switchMap((text) => (text.trim() ? this.companies.list(text) : of([]))),
      catchError(() => of([])),
    ),
    { initialValue: [] },
  );

  protected readonly payType = toSignal(this.form.controls.payType.valueChanges, {
    initialValue: this.form.controls.payType.value,
  });

  constructor() {
    this.applyPayType('salary');
    this.form.controls.payType.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((type) => this.applyPayType(type));
    this.form.controls.undisclosed.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((undisclosed) => this.applyUndisclosed(undisclosed));
  }

  ngOnInit(): void {
    if (this.isEdit()) {
      this.loadExisting();
    }
  }

  protected loadExisting(): void {
    this.loading.set(true);
    this.formError.set(null);
    this.stale.set(false);

    this.api
      .getById(this.id()!)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (app) => this.fill(app),
        error: (err: HttpErrorResponse) =>
          this.loadError.set(
            err.status === 404
              ? 'That application no longer exists.'
              : 'Could not load the application. Is the API running?',
          ),
      });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const request: CreateApplicationRequest = {
      // A name links to the user's company of that name, or creates it; none means undisclosed.
      companyName: v.undisclosed ? null : v.company.trim(),
      role: v.role,
      employmentType: v.employmentType || null,
      jobUrl: v.jobUrl.trim() || null,
      notes: v.notes.trim() || null,
      applyBy: v.applyBy || null,
      followUpOn: v.followUpOn || null,
      location: v.location.trim() || null,
      remote: v.remote === '' ? null : v.remote === 'yes',
      ...pay(v),
      salaryCurrency: v.salaryCurrency.trim().toUpperCase() || null,
      salaryText: v.salaryText.trim() || null,
      source: v.source.trim() || null,
      postedOn: v.postedOn || null,
      jobDescription: v.jobDescription.trim() || null,
    };

    this.submitting.set(true);
    this.formError.set(null);

    const id = this.id();
    const save$ = id
      ? this.api.update(id, { ...request, version: this.version })
      : this.api.create(request);

    save$.pipe(finalize(() => this.submitting.set(false))).subscribe({
      next: (saved) => this.router.navigate(id ? ['/applications', saved.id] : ['/']),
      error: (err: HttpErrorResponse) => this.applyServerErrors(err),
    });
  }

  protected errorFor(name: FieldName): string | null {
    const control = this.form.controls[name];
    if (!control.touched || !control.errors) return null;

    const e = control.errors;
    if (e['server']) return e['server'];
    if (e['required']) return 'Required.';
    if (e['maxlength']) return `Must be at most ${e['maxlength'].requiredLength} characters`;
    if (e['pattern']) return PATTERN_MESSAGES[name] ?? 'Invalid.';
    return 'Invalid.';
  }

  private applyUndisclosed(undisclosed: boolean): void {
    const company = this.form.controls.company;
    if (undisclosed) company.disable({ emitEvent: false });
    else company.enable({ emitEvent: false });
  }

  /** Only the active pay type's fields take part in validation and submission. */
  private applyPayType(type: PayType): void {
    const hourly = type === 'hourly';
    for (const name of SALARY_ONLY) {
      const control = this.form.controls[name];
      if (hourly) control.disable({ emitEvent: false });
      else control.enable({ emitEvent: false });
    }
    const rate = this.form.controls.hourlyRate;
    if (hourly) rate.enable({ emitEvent: false });
    else rate.disable({ emitEvent: false });
  }

  private fill(app: ApplicationResponse): void {
    this.version = app.version;
    this.form.reset({
      company: app.company ?? '',
      undisclosed: app.company == null,
      role: app.role,
      employmentType: app.employmentType ?? '',
      jobUrl: app.jobUrl ?? '',
      notes: app.notes ?? '',
      applyBy: app.applyBy ?? '',
      followUpOn: app.followUpOn ?? '',
      location: app.location ?? '',
      remote: app.remote == null ? '' : app.remote ? 'yes' : 'no',
      ...payFields(app),
      salaryCurrency: app.salaryCurrency ?? '',
      salaryText: app.salaryText ?? '',
      source: app.source ?? '',
      postedOn: app.postedOn ?? '',
      jobDescription: app.jobDescription ?? '',
    });
    this.applyUndisclosed(app.company == null);
    this.detailsOpen.set(hasPostingDetails(app));
  }

  private applyServerErrors(err: HttpErrorResponse): void {
    const problem = err.error as ProblemDetail | null;

    if (err.status === 400 && problem?.errors) {
      for (const [field, messages] of Object.entries(problem.errors)) {
        const name = formFieldFor(field, this.payType());
        const control = this.form.get(name);
        control?.setErrors({ server: messages[0] });
        control?.markAsTouched();
      }
      return;
    }

    if (err.status === 409) {
      // Keep the user's edits on screen; they choose whether to discard them.
      this.stale.set(true);
      this.formError.set('This application was changed somewhere else since you opened it.');
      return;
    }

    if (err.status === 404) {
      this.formError.set('That application no longer exists.');
      return;
    }

    this.formError.set('Could not save the application. Try again.');
  }
}

function hasPostingDetails(app: ApplicationResponse): boolean {
  return [
    app.location,
    app.remote,
    app.salaryMin,
    app.salaryMax,
    app.salaryText,
    app.source,
    app.postedOn,
    app.jobDescription,
  ].some((v) => v != null);
}

type FormValue = ReturnType<ApplicationForm['form']['getRawValue']>;

/** The request's pay fields for whichever pay type is chosen. */
function pay(
  v: Pick<FormValue, 'payType' | 'hourlyRate' | 'salaryMin' | 'salaryMax' | 'salaryPeriod'>,
): Pick<CreateApplicationRequest, 'salaryMin' | 'salaryMax' | 'salaryPeriod'> {
  if (v.payType === 'hourly') {
    const rate = v.hourlyRate ? Number(v.hourlyRate) : null;
    return { salaryMin: rate, salaryMax: null, salaryPeriod: rate == null ? null : 'hour' };
  }
  const min = v.salaryMin ? Number(v.salaryMin) : null;
  const max = v.salaryMax ? Number(v.salaryMax) : null;
  // The period only means something alongside an amount.
  return {
    salaryMin: min,
    salaryMax: max,
    salaryPeriod: (min ?? max) ? v.salaryPeriod || 'year' : null,
  };
}

/** Form values for an application's stored pay; "hour" means the hourly switch. */
function payFields(app: ApplicationResponse) {
  if (app.salaryPeriod === 'hour') {
    const rate = app.salaryMin ?? app.salaryMax;
    return {
      payType: 'hourly' as PayType,
      hourlyRate: rate?.toString() ?? '',
      salaryMin: '',
      salaryMax: '',
      salaryPeriod: 'year',
    };
  }
  return {
    payType: 'salary' as PayType,
    hourlyRate: '',
    salaryMin: app.salaryMin?.toString() ?? '',
    salaryMax: app.salaryMax?.toString() ?? '',
    salaryPeriod: app.salaryPeriod ?? 'year',
  };
}

/** The form control a server field error belongs to. */
function formFieldFor(field: string, payType: PayType): string {
  // An hourly rate travels as salaryMin, so its errors belong on the rate field.
  if (payType === 'hourly' && field === 'salaryMin') return 'hourlyRate';
  if (field === 'companyName' || field === 'companyId') return 'company';
  return field;
}
