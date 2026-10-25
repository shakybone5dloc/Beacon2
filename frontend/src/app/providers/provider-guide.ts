import { Component, computed, inject } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { ProvidersApi } from './providers-api';
import { accessLabel, Provider } from './provider.model';

/** How to get a key for each provider, and what each one costs. */
@Component({
  selector: 'app-provider-guide',
  imports: [RouterLink, NgTemplateOutlet],
  templateUrl: './provider-guide.html',
  styleUrl: './provider-guide.css',
})
export class ProviderGuide {
  private readonly api = inject(ProvidersApi);

  protected readonly providers = toSignal(
    this.api.providers().pipe(
      map((list) => ({ list, error: false })),
      catchError(() => of({ list: [] as Provider[], error: true })),
    ),
  );

  protected readonly jobSources = computed(
    () => this.providers()?.list.filter((p) => p.kind === 'JOB_SOURCE') ?? [],
  );
  protected readonly aiWriters = computed(
    () => this.providers()?.list.filter((p) => p.kind === 'AI') ?? [],
  );

  protected readonly accessLabel = accessLabel;
}
