import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  createPerson,
  deletePerson,
  getPerson,
  linkApplicationPerson,
  listApplicationPeople,
  listCompanyPeople,
  listPeople,
  listPersonApplications,
  unlinkApplicationPerson,
  updatePerson,
} from '../api/functions';
import {
  ApplicationPerson,
  CreatePersonRequest,
  Person,
  PersonApplication,
  PersonRole,
  UpdatePersonRequest,
} from './person.model';

@Injectable({ providedIn: 'root' })
export class PeopleApi {
  private readonly api = inject(BeaconApi);

  /** Everyone by name, or up to ten whose name contains {@code query}. */
  list(query?: string): Observable<Person[]> {
    return this.api.invoke(listPeople, { q: query?.trim() || undefined });
  }

  getById(id: string): Observable<Person> {
    return this.api.invoke(getPerson, { id });
  }

  create(request: CreatePersonRequest): Observable<Person> {
    return this.api.invoke(createPerson, { body: request });
  }

  update(id: string, request: UpdatePersonRequest): Observable<Person> {
    return this.api.invoke(updatePerson, { id, body: request });
  }

  delete(id: string): Observable<void> {
    return this.api.invoke(deletePerson, { id });
  }

  applicationsOf(id: string): Observable<PersonApplication[]> {
    return this.api.invoke(listPersonApplications, { id });
  }

  atCompany(companyId: string): Observable<Person[]> {
    return this.api.invoke(listCompanyPeople, { companyId });
  }

  onApplication(applicationId: string): Observable<ApplicationPerson[]> {
    return this.api.invoke(listApplicationPeople, { applicationId });
  }

  /** Adds them to the application, or changes their role there. A null role uses their usual one. */
  link(
    applicationId: string,
    personId: string,
    role: PersonRole | null,
  ): Observable<ApplicationPerson> {
    return this.api.invoke(linkApplicationPerson, { applicationId, personId, body: { role } });
  }

  unlink(applicationId: string, personId: string): Observable<void> {
    return this.api.invoke(unlinkApplicationPerson, { applicationId, personId });
  }
}
