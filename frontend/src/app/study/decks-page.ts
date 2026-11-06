import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { problemText } from '../documents/application-documents';
import { StudyApi } from './study-api';
import { Deck, dueLabel } from './study.model';

/** Every study deck, with what's due; decks for an application are made from its brief. */
@Component({
  selector: 'app-decks-page',
  imports: [RouterLink],
  templateUrl: './decks-page.html',
  host: { class: 'block max-w-4xl' },
})
export class DecksPage implements OnInit {
  private readonly api = inject(StudyApi);
  private readonly router = inject(Router);

  protected readonly decks = signal<Deck[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly adding = signal(false);
  protected readonly newName = signal('');
  protected readonly now = new Date();
  protected readonly dueLabel = dueLabel;

  ngOnInit(): void {
    this.api.decks().subscribe({
      next: (decks) => this.decks.set(decks),
      error: () => this.error.set('Could not load your decks. Is the API running?'),
    });
  }

  protected create(): void {
    const name = this.newName().trim();
    if (!name) return;
    this.api.createDeck(name).subscribe({
      next: (deck) => this.router.navigate(['/study', deck.id]),
      error: (err: HttpErrorResponse) =>
        this.error.set(problemText(err, 'Could not create the deck. Try again.')),
    });
  }
}
