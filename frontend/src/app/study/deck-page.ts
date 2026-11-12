import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { renderMarkdown } from '../briefs/brief.model';
import { problemText } from '../documents/application-documents';
import { StudyApi } from './study-api';
import { Card, Deck, dueLabel } from './study.model';

/**
 * One deck: drafts to keep or drop, the kept cards with when each is next due, a cheat
 * sheet with every answer, and adding or editing cards by hand.
 */
@Component({
  selector: 'app-deck-page',
  imports: [RouterLink],
  templateUrl: './deck-page.html',
  host: { class: 'block max-w-4xl' },
})
export class DeckPage implements OnInit {
  private readonly api = inject(StudyApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** The deck's id, from the route. */
  readonly id = input.required<string>();

  protected readonly deck = signal<Deck | null>(null);
  protected readonly cards = signal<Card[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly cheatSheet = signal(false);

  /** The card being edited, 'new' while adding one, or null when the form is closed. */
  protected readonly editing = signal<Card | 'new' | null>(null);
  protected readonly question = signal('');
  protected readonly answer = signal('');
  protected readonly saving = signal(false);

  protected readonly now = new Date();
  protected readonly dueLabel = dueLabel;
  protected readonly markdown = renderMarkdown;

  protected readonly drafts = computed(() => this.cards().filter((c) => c.status === 'DRAFT'));
  protected readonly kept = computed(() => this.cards().filter((c) => c.status === 'ACTIVE'));
  protected readonly due = computed(
    () => this.kept().filter((c) => new Date(c.dueAt).getTime() <= this.now.getTime()).length,
  );

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const added = params.get('added');
    if (added != null) {
      const skipped = Number(params.get('skipped') ?? 0);
      const cards = added === '1' ? 'card' : 'cards';
      const leftOut = skipped > 0 ? `; ${skipped} the deck already had were left out.` : '.';
      const from = params.get('from');
      this.notice.set(
        from === 'stories'
          ? // Story cards are the user's own words, so they're kept straight away.
            `Added ${added} story ${cards}${leftOut}`
          : `Made ${added} draft ${cards} ${from === 'interview' ? 'for the interview' : 'from the brief'}` +
              `${leftOut} Keep the ones worth drilling.`,
      );
    }
    this.load();
  }

  private load(): void {
    forkJoin({ deck: this.api.deck(this.id()), cards: this.api.cards(this.id()) }).subscribe({
      next: ({ deck, cards }) => {
        this.deck.set(deck);
        this.cards.set(cards);
      },
      error: (err: HttpErrorResponse) =>
        this.error.set(
          err.status === 404 ? 'That deck no longer exists.' : 'Could not load the deck.',
        ),
    });
  }

  protected startNew(): void {
    this.question.set('');
    this.answer.set('');
    this.editing.set('new');
  }

  protected edit(card: Card): void {
    this.question.set(card.question);
    this.answer.set(card.answer);
    this.editing.set(card);
  }

  protected save(): void {
    const target = this.editing();
    const question = this.question().trim();
    if (!target || !question) return;
    const call =
      target === 'new'
        ? this.api.addCard(this.id(), question, this.answer())
        : this.api.updateCard(target, question, this.answer());
    this.saving.set(true);
    call.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(null);
        this.cards.update((list) =>
          target === 'new' ? [...list, saved] : list.map((c) => (c.id === saved.id ? saved : c)),
        );
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.notice.set(
          err.status === 409
            ? 'This card was changed somewhere else. Reload the page and try again.'
            : problemText(err, 'Could not save the card. Try again.'),
        );
      },
    });
  }

  protected keep(card: Card): void {
    this.api.keep(card.id).subscribe({
      next: (kept) => this.replace(kept),
      error: () => this.notice.set('Could not keep the card. Try again.'),
    });
  }

  protected keepAll(): void {
    this.api.keepAll(this.id()).subscribe({
      next: () => this.load(),
      error: () => this.notice.set('Could not keep the drafts. Try again.'),
    });
  }

  protected remove(card: Card): void {
    if (!confirm('Delete this card?')) return;
    this.api.deleteCard(card.id).subscribe({
      next: () => this.cards.update((list) => list.filter((c) => c.id !== card.id)),
      error: () => this.notice.set('Could not delete the card. Try again.'),
    });
  }

  protected removeDeck(deck: Deck): void {
    if (!confirm(`Delete the deck "${deck.name}" and all its cards?`)) return;
    this.api.deleteDeck(deck.id).subscribe({
      next: () => this.router.navigate(['/study']),
      error: () => this.notice.set('Could not delete the deck. Try again.'),
    });
  }

  private replace(card: Card): void {
    this.cards.update((list) => list.map((c) => (c.id === card.id ? card : c)));
  }
}
