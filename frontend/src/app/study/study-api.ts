import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BeaconApi } from '../api/beacon-api';
import {
  addCard,
  checkAnswer,
  createDeck,
  deleteCard,
  deleteDeck,
  drillStories,
  getDeck,
  getRecallGrading,
  keepCard,
  keepDeckDrafts,
  listDeckCards,
  listDecks,
  listDueCards,
  makeInterviewStudyCards,
  makeStudyCards,
  renameDeck,
  reviewCard,
  updateCard,
} from '../api/functions';
import { map } from 'rxjs';
import { AnswerCheck, AnswerMode, Card, Deck, Grade, MadeCards } from './study.model';

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

  /**
   * Grades a card. In Type mode, what was typed and the AI's suggestion (when it was asked)
   * are kept with the grade; the grade given here is the one that schedules the card.
   */
  review(
    id: string,
    grade: Grade,
    answer: { mode: AnswerMode; text?: string | null; check?: AnswerCheck | null } = { mode: 'REVEAL' },
  ): Observable<Card> {
    return this.api.invoke(reviewCard, {
      id,
      body: {
        grade,
        mode: answer.mode,
        answer: answer.text || null,
        suggestedGrade: answer.check?.suggestedGrade ?? null,
        feedback: answer.check?.feedback ?? null,
      },
    });
  }

  /** The AI's suggested grade and note for a typed answer; saves nothing. */
  check(id: string, answer: string): Observable<AnswerCheck> {
    return this.api.invoke(checkAnswer, { id, body: { answer } });
  }

  /** Whether the user has a key, so typed answers can be checked. */
  gradingAvailable(): Observable<boolean> {
    return this.api.invoke(getRecallGrading).pipe(map((r) => r.available));
  }

  /** Draft cards from the application's brief and its stories, in the application's deck. */
  fromBrief(applicationId: string): Observable<MadeCards> {
    return this.api.invoke(makeStudyCards, { applicationId });
  }

  /**
   * "Tell the story" cards for these stories (all of them when null) in the Stories deck, kept
   * straight away; ones already there are skipped.
   */
  drillStories(storyIds: string[] | null): Observable<MadeCards> {
    return this.api.invoke(drillStories, { body: { storyIds } });
  }

  /** Draft cards for one interview: the brief plus its prep notes, or the posting and fit gaps. */
  fromInterview(interviewId: string): Observable<MadeCards> {
    return this.api.invoke(makeInterviewStudyCards, { interviewId });
  }
}
