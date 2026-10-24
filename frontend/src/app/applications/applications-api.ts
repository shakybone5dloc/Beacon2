import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApplicationResponse,
  ApplicationStatus,
  ChangeStatusRequest,
  CreateApplicationRequest,
  FollowUpRequest,
  StatusChange,
  UpdateApplicationRequest,
} from './application.model';

@Injectable({ providedIn: 'root' })
export class ApplicationsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/applications';

  list(): Observable<ApplicationResponse[]> {
    return this.http.get<ApplicationResponse[]>(this.baseUrl);
  }

  getById(id: string): Observable<ApplicationResponse> {
    return this.http.get<ApplicationResponse>(`${this.baseUrl}/${id}`);
  }

  history(id: string): Observable<StatusChange[]> {
    return this.http.get<StatusChange[]>(`${this.baseUrl}/${id}/history`);
  }

  changeStatus(
    id: string,
    status: ApplicationStatus,
    version: number,
  ): Observable<ApplicationResponse> {
    const body: ChangeStatusRequest = { status, version };
    return this.http.patch<ApplicationResponse>(`${this.baseUrl}/${id}/status`, body);
  }

  create(request: CreateApplicationRequest): Observable<ApplicationResponse> {
    return this.http.post<ApplicationResponse>(`${this.baseUrl}`, request);
  }

  update(id: string, request: UpdateApplicationRequest): Observable<ApplicationResponse> {
    return this.http.put<ApplicationResponse>(`${this.baseUrl}/${id}`, request);
  }

  scheduleFollowUp(
    id: string,
    followUpOn: string | null,
    version: number,
  ): Observable<ApplicationResponse> {
    const body: FollowUpRequest = { followUpOn, version };
    return this.http.put<ApplicationResponse>(`${this.baseUrl}/${id}/follow-up`, body);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
