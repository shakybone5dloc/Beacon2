import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiKeyEditor } from './api-key-editor';
import { ProvidersApi } from './providers-api';
import { ApiKeyStatus, Provider } from './provider.model';

interface Entry {
  provider: Provider;
  status: ApiKeyStatus;
}

/** Settings: the user's own keys for every provider that takes one. */
@Component({
  selector: 'app-api-keys-page',
  imports: [RouterLink, ApiKeyEditor],
  templateUrl: './api-keys-page.html',
  styleUrl: './api-keys-page.css',
})
export class ApiKeysPage implements OnInit {
  private readonly api = inject(ProvidersApi);

  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  private readonly entries = signal<Entry[]>([]);

  protected readonly aiWriters = computed(() =>
    this.entries().filter((e) => e.provider.kind === 'AI'),
  );
  protected readonly jobSources = computed(() =>
    this.entries().filter((e) => e.provider.kind === 'JOB_SOURCE'),
  );

  ngOnInit(): void {
    forkJoin({ providers: this.api.providers(), keys: this.api.keys() }).subscribe({
      next: ({ providers, keys }) => {
        this.entries.set(
          providers
            .filter((p) => p.fields.length > 0)
            .map((provider) => ({
              provider,
              status: keys.find((k) => k.provider === provider.id) ?? {
                provider: provider.id,
                saved: false,
                hint: null,
                updatedAt: null,
              },
            })),
        );
        this.loading.set(false);
        // The guide links straight to one provider; the section exists only now.
        const target = location.hash.slice(1);
        if (target) setTimeout(() => document.getElementById(target)?.scrollIntoView());
      },
      error: () => {
        this.loadError.set(true);
        this.loading.set(false);
      },
    });
  }

  protected update(status: ApiKeyStatus): void {
    this.entries.update((list) =>
      list.map((e) => (e.provider.id === status.provider ? { ...e, status } : e)),
    );
  }
}
