import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  changeApplicationStatus,
  createApplication,
  deleteApplication,
  getApplication,
  getApplicationHistory,
  listApplications,
  scheduleFollowUp,
  updateApplication,
} from '../api/functions';
import {
  ApplicationResponse,
  ApplicationStatus,
  CreateApplicationRequest,
  StatusChange,
  UpdateApplicationRequest,
} from './application.model';

@Injectable({ providedIn: 'root' })
export class ApplicationsApi {
  private readonly api = inject(BeaconApi);

  list(): Observable<ApplicationResponse[]> {
    return this.api.invoke(listApplications);
  }

  getById(id: string): Observable<ApplicationResponse> {
    return this.api.invoke(getApplication, { id });
  }

  history(id: string): Observable<StatusChange[]> {
    return this.api.invoke(getApplicationHistory, { id });
  }

  changeStatus(
    id: string,
    status: ApplicationStatus,
    version: number,
  ): Observable<ApplicationResponse> {
    return this.api.invoke(changeApplicationStatus, { id, body: { status, version } });
  }

  create(request: CreateApplicationRequest): Observable<ApplicationResponse> {
    return this.api.invoke(createApplication, { body: request });
  }

  update(id: string, request: UpdateApplicationRequest): Observable<ApplicationResponse> {
    return this.api.invoke(updateApplication, { id, body: request });
  }

  scheduleFollowUp(
    id: string,
    followUpOn: string | null,
    version: number,
  ): Observable<ApplicationResponse> {
    return this.api.invoke(scheduleFollowUp, { id, body: { followUpOn, version } });
  }

  delete(id: string): Observable<void> {
    return this.api.invoke(deleteApplication, { id });
  }
}
