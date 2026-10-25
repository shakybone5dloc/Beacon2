import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiKeyStatus, Provider } from './provider.model';

@Injectable({ providedIn: 'root' })
export class ProvidersApi {
  private readonly http = inject(HttpClient);

  providers(): Observable<Provider[]> {
    return this.http.get<Provider[]>('/api/providers');
  }

  keys(): Observable<ApiKeyStatus[]> {
    return this.http.get<ApiKeyStatus[]>('/api/me/api-keys');
  }

  saveKey(provider: string, values: Record<string, string>): Observable<ApiKeyStatus> {
    return this.http.put<ApiKeyStatus>(`/api/me/api-keys/${provider}`, { values });
  }

  removeKey(provider: string): Observable<void> {
    return this.http.delete<void>(`/api/me/api-keys/${provider}`);
  }
}
