import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { ApplicationResponse } from '../applications/application.model';
import { PostingView } from './posting-view';

type Tab = 'view' | 'pdf' | 'live';

/**
 * Side panel on the application page: the posting as Beacon stored it, the
 * saved posting PDF, or the live job page in a frame.
 */
@Component({
  selector: 'app-posting-panel',
  imports: [PostingView, RouterLink],
  templateUrl: './posting-panel.html',
  styleUrl: './posting-panel.css',
})
export class PostingPanel {
  private readonly sanitizer = inject(DomSanitizer);

  readonly application = input.required<ApplicationResponse>();
  /** Content URL of the newest saved posting document, if any. */
  readonly postingPdfUrl = input<string | null>(null);

  protected readonly tab = signal<Tab>('view');

  /** Beacon's own document URL, so it always loads in a frame. */
  protected readonly pdfSrc = computed<SafeResourceUrl | null>(() => {
    const url = this.postingPdfUrl();
    return url ? this.sanitizer.bypassSecurityTrustResourceUrl(url) : null;
  });

  /**
   * Only other sites' web pages are framed. The sandbox lets job pages run their
   * scripts and storage, which is safe only while the page is a different origin
   * from Beacon, so Beacon's own origin (and non-web URLs) are never framed.
   */
  protected readonly liveSrc = computed<SafeResourceUrl | null>(() => {
    const url = this.application().jobUrl;
    if (!url || !/^https?:\/\//i.test(url)) return null;
    try {
      if (new URL(url).origin === window.location.origin) return null;
    } catch {
      return null;
    }
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
  });

  constructor() {
    // A tab whose content went away (the PDF was deleted) falls back to Beacon's view.
    effect(() => {
      if ((this.tab() === 'pdf' && !this.pdfSrc()) || (this.tab() === 'live' && !this.liveSrc())) {
        this.tab.set('view');
      }
    });
  }
}
