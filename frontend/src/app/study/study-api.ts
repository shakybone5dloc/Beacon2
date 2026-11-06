import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  addCard,
  createDeck,
  deleteCard,
  deleteDeck,
  getDeck,
  keepCard,
  keepDeckDrafts,
  listDeckCards,
  listDecks,
  listDueCards,
  makeStudyCards,
  renameDeck,
  reviewCard,
  updateCard,
} from '../api/functions';
import { Card, Deck, Grade, MadeCards } from './study.model';

/** Study decks and cards: keep them, study what's due, and make drafts from a brief. */
@Injectable({ providedIn: 'root' })
export class StudyApi {
  private readonly api = inject(BeaconApi);

  decks(): Observable<Deck[]> {
    return this.api.invoke(listDecks);
  }

  deck(id: string): Observable<Deck> {
    return this.api.invoke(getDeck, { id });
  }

  createDeck(name: string): Observable<Deck> {
    return this.api.invoke(createDeck, { body: { name, version: null } });
  }

  renameDeck(deck: Deck, name: string): Observable<Deck> {
    return this.api.invoke(renameDeck, { id: deck.id, body: { name, version: deck.version } });
  }

  deleteDeck(id: string): Observable<void> {
    return this.api.invoke(deleteDeck, { id });
  }

  /** Every card, drafts included, oldest first. */
  cards(deckId: string): Observable<Card[]> {
    return this.api.invoke(listDeckCards, { id: deckId });
  }

  /** Kept cards due now, most overdue first. */
  due(deckId: string): Observable<Card[]> {
    return this.api.invoke(listDueCards, { id: deckId });
  }

  addCard(deckId: string, question: string, answer: string): Observable<Card> {
    return this.api.invoke(addCard, { id: deckId, body: { question, answer, version: null } });
  }

  updateCard(card: Card, question: string, answer: string): Observable<Card> {
    return this.api.invoke(updateCard, {
      id: card.id,
      body: { question, answer, version: card.version },
    });
  }

  deleteCard(id: string): Observable<void> {
    return this.api.invoke(deleteCard, { id });
  }

  keep(id: string): Observable<Card> {
    return this.api.invoke(keepCard, { id });
  }

  keepAll(deckId: string): Observable<Deck> {
    return this.api.invoke(keepDeckDrafts, { id: deckId });
  }

  review(id: string, grade: Grade): Observable<Card> {
    return this.api.invoke(reviewCard, { id, body: { grade } });
  }

  /** Draft cards from the application's brief and its stories, in the application's deck. */
  fromBrief(applicationId: string): Observable<MadeCards> {
    return this.api.invoke(makeStudyCards, { applicationId });
  }
}
