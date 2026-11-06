import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { getStats } from '../api/functions';
import { Stats } from './stats.model';

@Injectable({ providedIn: 'root' })
export class StatsApi {
  private readonly api = inject(BeaconApi);

  /** @param days only applications created in the last this many days; null for all of them */
  get(days: number | null, zone: string): Observable<Stats> {
    return this.api.invoke(getStats, { days: days ?? undefined, zone });
  }
}
