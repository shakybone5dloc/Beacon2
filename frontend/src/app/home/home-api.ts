import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import { getHomeTodos } from '../api/functions';
import { HomeTodos } from './home.model';

/** The part of Home no other endpoint gives; the rest comes from the pages' own APIs. */
@Injectable({ providedIn: 'root' })
export class HomeApi {
  private readonly api = inject(BeaconApi);

  todos(): Observable<HomeTodos> {
    return this.api.invoke(getHomeTodos);
  }
}
