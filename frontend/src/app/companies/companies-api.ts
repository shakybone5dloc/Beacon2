import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  createCompany,
  getCompany,
  listCompanies,
  listCompanyApplications,
  listCompanyOpenings,
  stopWatchingCompany,
  updateCompany,
  watchCompany,
} from '../api/functions';
import { ApplicationResponse } from '../applications/application.model';
import { Company, CreateCompanyRequest, Opening, UpdateCompanyRequest } from './company.model';

@Injectable({ providedIn: 'root' })
export class CompaniesApi {
  private readonly api = inject(BeaconApi);

  /** Every company by name, or up to ten whose name contains {@code query}. */
  list(query?: string): Observable<Company[]> {
    return this.api.invoke(listCompanies, { q: query?.trim() || undefined });
  }

  /** A name the user already has gives that company back instead of a duplicate. */
  create(request: CreateCompanyRequest): Observable<Company> {
    return this.api.invoke(createCompany, { body: request });
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

  /** Puts the company on the watchlist from a link to its careers page. */
  watch(id: string, url: string): Observable<Company> {
    return this.api.invoke(watchCompany, { id, body: { url } });
  }

  stopWatching(id: string): Observable<Company> {
    return this.api.invoke(stopWatchingCompany, { id });
  }

  /** What's open on the company's careers board, newest first. */
  openings(id: string): Observable<Opening[]> {
    return this.api.invoke(listCompanyOpenings, { id });
  }
}
