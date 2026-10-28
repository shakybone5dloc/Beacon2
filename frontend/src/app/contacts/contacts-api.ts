import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  createContact,
  deleteContact,
  listApplicationContacts,
  listPersonContacts,
  listPossibleDuplicates,
  updateContact,
} from '../api/functions';
import {
  Contact,
  CreateContactRequest,
  PossibleDuplicate,
  UpdateContactRequest,
} from './contact.model';

@Injectable({ providedIn: 'root' })
export class ContactsApi {
  private readonly api = inject(BeaconApi);

  /** A person's log, newest first. */
  withPerson(personId: string): Observable<Contact[]> {
    return this.api.invoke(listPersonContacts, { personId });
  }

  /** Contacts about one application, newest first. */
  aboutApplication(applicationId: string): Observable<Contact[]> {
    return this.api.invoke(listApplicationContacts, { applicationId });
  }

  /** Logging a contact about an application also puts the person on it. */
  create(request: CreateContactRequest): Observable<Contact> {
    return this.api.invoke(createContact, { body: request });
  }

  update(id: string, request: UpdateContactRequest): Observable<Contact> {
    return this.api.invoke(updateContact, { id, body: request });
  }

  delete(id: string): Observable<void> {
    return this.api.invoke(deleteContact, { id });
  }

  /** Other applications that look like the same job, e.g. pitched by a second recruiter. */
  possibleDuplicates(applicationId: string): Observable<PossibleDuplicate[]> {
    return this.api.invoke(listPossibleDuplicates, { id: applicationId });
  }
}
