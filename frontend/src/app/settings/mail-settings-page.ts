import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { CheckInApi } from '../contacts/check-in-api';
import { SendFrom } from '../contacts/check-in.model';

/**
 * Settings: the address the user sends Beacon-written emails (like a check-in) from. A Gmail
 * address opens Gmail as that account; any other opens the device's mail app.
 */
@Component({
  selector: 'app-mail-settings-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './mail-settings-page.html',
  host: { class: 'block max-w-2xl' },
})
export class MailSettingsPage implements OnInit {
  private readonly api = inject(CheckInApi);

  protected readonly settings = signal<SendFrom | null>(null);
  protected readonly draft = signal('');
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly notice = signal<string | null>(null);

  /** True when the field differs from what's saved. */
  protected readonly changed = computed(() => {
    const s = this.settings();
    return s != null && this.draft().trim().toLowerCase() !== s.sendFrom;
  });

  ngOnInit(): void {
    this.api.sendFrom().subscribe({
      next: (settings) => this.loaded(settings),
      error: () => this.loadError.set(true),
    });
  }

  protected save(): void {
    this.store(this.draft().trim() || null);
  }

  /** Goes back to the address the user signs in with. */
  protected useSignIn(): void {
    this.store(null);
  }

  private store(address: string | null): void {
    this.saving.set(true);
    this.saved.set(false);
    this.notice.set(null);
    this.api
      .saveSendFrom(address)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (settings) => {
          this.loaded(settings);
          this.saved.set(true);
        },
        error: () => this.notice.set("That doesn't look like an email address."),
      });
  }

  private loaded(settings: SendFrom): void {
    this.settings.set(settings);
    this.draft.set(settings.sendFrom);
  }
}
