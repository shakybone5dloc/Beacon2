import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { CheckInApi } from '../contacts/check-in-api';
import { MailClient } from '../contacts/check-in.model';

/** Settings: where the emails the user writes from Beacon (like a check-in) open. */
@Component({
  selector: 'app-mail-settings-page',
  imports: [RouterLink],
  templateUrl: './mail-settings-page.html',
  host: { class: 'block max-w-2xl' },
})
export class MailSettingsPage implements OnInit {
  private readonly api = inject(CheckInApi);

  protected readonly mailClient = signal<MailClient | null>(null);
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly notice = signal<string | null>(null);

  protected readonly options: { value: MailClient; label: string; hint: string }[] = [
    {
      value: 'GMAIL',
      label: 'Gmail',
      hint: 'A Gmail compose window in a new browser tab, from whichever account you are signed in to.',
    },
    {
      value: 'MAIL_APP',
      label: 'My mail app',
      hint: "The mail program this device uses for email links, like Outlook, Apple Mail or your phone's mail app.",
    },
  ];

  ngOnInit(): void {
    this.api.mailClient().subscribe({
      next: (client) => this.mailClient.set(client),
      error: () => this.loadError.set(true),
    });
  }

  protected choose(client: MailClient): void {
    const before = this.mailClient();
    this.mailClient.set(client);
    this.saving.set(true);
    this.saved.set(false);
    this.notice.set(null);
    this.api
      .saveMailClient(client)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => this.saved.set(true),
        error: () => {
          this.mailClient.set(before);
          this.notice.set('Could not save that. Try again.');
        },
      });
  }
}
