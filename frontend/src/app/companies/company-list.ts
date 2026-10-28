import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CompaniesApi } from './companies-api';
import { Company, COMPANY_TYPE_LABELS } from './company.model';

/** Every company the user has applied to or been pitched by. */
@Component({
  selector: 'app-company-list',
  imports: [RouterLink, DatePipe],
  templateUrl: './company-list.html',
  styleUrl: './companies.css',
  host: { class: 'max-w-5xl' },
})
export class CompanyList implements OnInit {
  private readonly api = inject(CompaniesApi);

  protected readonly companies = signal<Company[] | null>(null);
  protected readonly error = signal(false);
  protected readonly typeLabels = COMPANY_TYPE_LABELS;

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (companies) => this.companies.set(companies),
      error: () => this.error.set(true),
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
