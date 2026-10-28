import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  getCompany,
  listCompanies,
  listCompanyApplications,
  updateCompany,
} from '../api/functions';
import { ApplicationResponse } from '../applications/application.model';
import { Company, UpdateCompanyRequest } from './company.model';

@Injectable({ providedIn: 'root' })
export class CompaniesApi {
  private readonly api = inject(BeaconApi);

  /** Every company by name, or up to ten whose name contains {@code query}. */
  list(query?: string): Observable<Company[]> {
    return this.api.invoke(listCompanies, { q: query?.trim() || undefined });
  }

  getById(id: string): Observable<Company> {
    return this.api.invoke(getCompany, { id });
  }

  applications(id: string): Observable<ApplicationResponse[]> {
    return this.api.invoke(listCompanyApplications, { id });
  }

  update(id: string, request: UpdateCompanyRequest): Observable<Company> {
    return this.api.invoke(updateCompany, { id, body: request });
  }
}
