import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { CompaniesApi } from './companies-api';
import { Company, COMPANY_TYPE_LABELS, COMPANY_TYPES, CompanyType } from './company.model';

/** Every company the user has applied to, been pitched by, or added to watch. */
@Component({
  selector: 'app-company-list',
  imports: [RouterLink, DatePipe, ReactiveFormsModule],
  templateUrl: './company-list.html',
  styleUrl: './companies.css',
  host: { class: 'max-w-5xl' },
})
export class CompanyList implements OnInit {
  private readonly api = inject(CompaniesApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly companies = signal<Company[] | null>(null);
  protected readonly error = signal(false);
  protected readonly typeLabels = COMPANY_TYPE_LABELS;
  protected readonly types = COMPANY_TYPES;

  protected readonly adding = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    type: ['EMPLOYER' as CompanyType],
    website: [''],
  });

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (companies) => this.companies.set(companies),
      error: () => this.error.set(true),
    });
  }

  protected startAdding(): void {
    this.form.reset({ name: '', type: 'EMPLOYER', website: '' });
    this.formError.set(null);
    this.adding.set(true);
  }

  /** Opens the new company, or the existing one when the name is already taken. */
  protected add(): void {
    const v = this.form.getRawValue();
    if (this.form.invalid || !v.name.trim()) {
      this.formError.set('Give the company a name.');
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .create({ name: v.name.trim(), type: v.type, website: v.website.trim() || null })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (company) => this.router.navigate(['/companies', company.id]),
        error: (err: HttpErrorResponse) =>
          this.formError.set(problemText(err, 'Could not add the company. Try again.')),
      });
  }

  /** "Robert Half Technology" -> "RH"; one word gives its first letter. */
  protected initials(name: string): string {
    const words = name.split(/\s+/).filter((w) => /[A-Za-z0-9]/.test(w));
    return words
      .slice(0, 2)
      .map((w) => w[0])
      .join('')
      .toUpperCase();
  }

  /** "https://www.acme.com/careers" -> "acme.com"; null without a usable website. */
  protected domain(company: Company): string | null {
    if (!company.website) return null;
    try {
      return new URL(company.website).hostname.replace(/^www\./, '');
    } catch {
      return null;
    }
  }
}
