import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CompaniesApi } from './companies-api';
import { Company, COMPANY_TYPE_LABELS } from './company.model';

/** Every company the user has applied to or been pitched by. */
@Component({
  selector: 'app-company-list',
  imports: [RouterLink],
  templateUrl: './company-list.html',
  styleUrl: './companies.css',
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
}
