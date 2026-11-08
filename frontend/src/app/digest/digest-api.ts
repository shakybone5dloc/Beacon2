import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  getDigestSettings,
  previewDigest,
  saveDigestSettings,
  sendDigestNow,
} from '../api/functions';
import { DigestPreview, DigestSettings, SaveDigestSettings } from './digest.model';

/** The weekly digest email: when it goes out, a preview, and sending one now. */
@Injectable({ providedIn: 'root' })
export class DigestApi {
  private readonly api = inject(BeaconApi);

  /** @param zone the browser's zone, for the defaults before anything is saved */
  get(zone: string): Observable<DigestSettings> {
    return this.api.invoke(getDigestSettings, { zone });
  }

  save(body: SaveDigestSettings): Observable<DigestSettings> {
    return this.api.invoke(saveDigestSettings, { body });
  }

  preview(zone: string): Observable<DigestPreview> {
    return this.api.invoke(previewDigest, { zone });
  }

  sendNow(zone: string): Observable<void> {
    return this.api.invoke(sendDigestNow, { zone });
  }
}
