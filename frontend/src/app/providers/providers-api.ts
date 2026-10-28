import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { deleteApiKey, listApiKeys, listProviders, saveApiKey } from '../api/functions';
import { ApiKeyStatus, Provider } from './provider.model';

@Injectable({ providedIn: 'root' })
export class ProvidersApi {
  private readonly api = inject(BeaconApi);

  providers(): Observable<Provider[]> {
    return this.api.invoke(listProviders);
  }

  keys(): Observable<ApiKeyStatus[]> {
    return this.api.invoke(listApiKeys);
  }

  saveKey(provider: string, values: Record<string, string>): Observable<ApiKeyStatus> {
    return this.api.invoke(saveApiKey, { provider, body: { values } });
  }

  removeKey(provider: string): Observable<void> {
    return this.api.invoke(deleteApiKey, { provider });
  }
}
