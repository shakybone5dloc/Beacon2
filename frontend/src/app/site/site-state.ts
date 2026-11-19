import { computed, inject, Injectable, signal } from '@angular/core';
import { BeaconApi } from '../api/beacon-api';
import { getSite } from '../api/functions';

/** The address for feedback and privacy questions, loaded once; null until it arrives or if unset. */
@Injectable({ providedIn: 'root' })
export class SiteState {
  private readonly api = inject(BeaconApi);
  private requested = false;

  private readonly email = signal<string | null>(null);

  readonly contactEmail = this.email.asReadonly();

  /** A mailto link that starts a feedback email, or null when there's no address. */
  readonly feedbackLink = computed(() => {
    const email = this.email();
    return email ? `mailto:${email}?subject=${encodeURIComponent('Beacon feedback')}` : null;
  });

  load(): void {
    if (this.requested) return;
    this.requested = true;
    this.api.invoke(getSite).subscribe({
      next: (site) => this.email.set(site.contactEmail),
      error: () => (this.requested = false),
    });
  }
}
