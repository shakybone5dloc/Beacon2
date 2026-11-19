import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { Invite, InvitesApi, inviteLink } from './invites-api';

const STATE_LABELS: Record<string, string> = {
  OPEN: 'Not used yet',
  USED: 'Used',
  EXPIRED: 'Expired',
  REVOKED: 'Revoked',
};

/**
 * Settings, admins only: the invite codes for the invite-only beta. Each code makes one account;
 * share it as a link that opens the sign-up page with the code filled in.
 */
@Component({
  selector: 'app-invites-page',
  imports: [DatePipe, FormsModule],
  templateUrl: './invites-page.html',
  host: { class: 'block max-w-3xl' },
})
export class InvitesPage implements OnInit {
  private readonly api = inject(InvitesApi);

  protected readonly invites = signal<Invite[] | null>(null);
  protected readonly loadError = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly creating = signal(false);
  protected readonly note = signal('');
  protected readonly days = signal(14);
  /** The code whose link was just copied, to say so beside it. */
  protected readonly copied = signal<string | null>(null);

  protected readonly stateLabels = STATE_LABELS;
  protected readonly link = (code: string) => inviteLink(code);

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (invites) => this.invites.set(invites),
      error: () => this.loadError.set(true),
    });
  }

  protected create(): void {
    this.creating.set(true);
    this.notice.set(null);
    this.api
      .create(this.note().trim() || null, this.days())
      .pipe(finalize(() => this.creating.set(false)))
      .subscribe({
        next: (invite) => {
          this.invites.update((list) => [invite, ...(list ?? [])]);
          this.note.set('');
        },
        error: () => this.notice.set('Could not make the code. Try again.'),
      });
  }

  protected revoke(invite: Invite): void {
    if (!confirm(`Revoke ${invite.code}? It stops working straight away.`)) return;
    this.api.revoke(invite.id).subscribe({
      next: (revoked) =>
        this.invites.update((list) => (list ?? []).map((i) => (i.id === revoked.id ? revoked : i))),
      error: () => this.notice.set('Could not revoke it. Try again.'),
    });
  }

  protected async copy(invite: Invite): Promise<void> {
    try {
      await navigator.clipboard.writeText(inviteLink(invite.code));
      this.copied.set(invite.code);
    } catch {
      this.notice.set('Could not copy. Select the link and copy it by hand.');
    }
  }
}
