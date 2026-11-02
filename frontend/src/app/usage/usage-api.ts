import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { getAiUsage, saveAiBudget } from '../api/functions';
import { AiUsage, browserZone } from './usage.model';

/** What the signed-in user has spent on AI this month, and their monthly budget. */
@Injectable({ providedIn: 'root' })
export class UsageApi {
  private readonly api = inject(BeaconApi);

  summary(): Observable<AiUsage> {
    return this.api.invoke(getAiUsage, { zone: browserZone() });
  }

  /** Null removes the budget. Returns the summary with the new budget in it. */
  saveBudget(monthlyUsd: number | null): Observable<AiUsage> {
    return this.api.invoke(saveAiBudget, { zone: browserZone(), body: { monthlyUsd } });
  }
}
