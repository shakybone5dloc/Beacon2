import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApplicationResponse,
  ChangeStatusRequest,
  CreateApplicationRequest,
} from './application.model';
import { ApplicationStatus } from './application.model';

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
}
