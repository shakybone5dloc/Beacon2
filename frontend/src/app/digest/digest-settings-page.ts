import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { browserZone } from '../usage/usage.model';
import { DigestApi } from './digest-api';
import { DAYS, DigestSettings, HOURS, SaveDigestSettings } from './digest.model';

/**
 * Settings · Weekly email (A5): Home's sections emailed on a day and hour the user picks,
 * with a preview of this week's email and a button to send one now.
 */
@Component({
  selector: 'app-digest-settings-page',
  imports: [DatePipe, RouterLink],
  templateUrl: './digest-settings-page.html',
  host: { class: 'block max-w-3xl' },
})
export class DigestSettingsPage implements OnInit {
  private readonly api = inject(DigestApi);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly zone = browserZone();

  protected readonly settings = signal<DigestSettings | null>(null);
  protected readonly loadError = signal(false);
  protected readonly saving = signal(false);
  protected readonly sending = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly saved = signal(false);
  protected readonly sent = signal(false);
  protected readonly previewSubject = signal<string | null>(null);
  protected readonly previewHtml = signal<SafeHtml | null>(null);
  protected readonly previewing = signal(false);

  protected readonly days = DAYS;
  protected readonly hours = HOURS;

  ngOnInit(): void {
    this.api.get(this.zone).subscribe({
      next: (s) => this.settings.set(s),
      error: () => this.loadError.set(true),
    });
  }

  /** Saves on every change, as a toggle or picker would. */
  protected change(patch: Partial<SaveDigestSettings>): void {
    const current = this.settings();
    if (!current) return;
    this.saving.set(true);
    this.saved.set(false);
    this.notice.set(null);
    this.api
      .save({
        enabled: current.enabled,
        dayOfWeek: current.dayOfWeek,
        hour: current.hour,
        // The browser's zone, so the hour means what the user sees on their clock.
        zone: this.zone,
        version: current.version,
        ...patch,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (s) => {
          this.settings.set(s);
          this.saved.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this.notice.set(
            err.status === 409
              ? 'These settings were changed in another tab. Reload to see them.'
              : problemText(err, 'Could not save. Try again.'),
          );
        },
      });
  }

  protected preview(): void {
    this.previewing.set(true);
    this.notice.set(null);
    this.api
      .preview(this.zone)
      .pipe(finalize(() => this.previewing.set(false)))
      .subscribe({
        next: (p) => {
          this.previewSubject.set(p.subject);
          // Built by Beacon's server with every name escaped, and shown in a sandboxed frame that runs no scripts.
          this.previewHtml.set(this.sanitizer.bypassSecurityTrustHtml(p.html));
        },
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not build the preview. Try again.')),
      });
  }

  protected sendNow(): void {
    this.sending.set(true);
    this.sent.set(false);
    this.notice.set(null);
    this.api
      .sendNow(this.zone)
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: () => this.sent.set(true),
        error: (err: HttpErrorResponse) =>
          this.notice.set(problemText(err, 'Could not send it. Try again.')),
      });
  }
}
