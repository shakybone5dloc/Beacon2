import { Component, computed, HostListener, inject, input, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { renderMarkdown } from '../briefs/brief.model';
import { StudyApi } from './study-api';
import { Card, Deck, Grade, GRADES } from './study.model';

/**
 * Studying a deck's due cards: read the question, answer out loud, reveal, grade. A card
 * graded Again goes back in the queue and comes round again before the session ends.
 * Space or Enter reveals; 1 to 4 grade.
 */
@Component({
  selector: 'app-study-session',
  imports: [RouterLink],
  templateUrl: './study-session.html',
  host: { class: 'block max-w-2xl mx-auto' },
})
export class StudySession implements OnInit {
  private readonly api = inject(StudyApi);

  /** The deck's id, from the route. */
  readonly id = input.required<string>();

  protected readonly deck = signal<Deck | null>(null);
  protected readonly queue = signal<Card[] | null>(null);
  protected readonly revealed = signal(false);
  protected readonly grading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Cards studied this session, and how many were missed at least once. */
  protected readonly studied = signal<ReadonlySet<string>>(new Set());
  protected readonly missed = signal<ReadonlySet<string>>(new Set());

  protected readonly grades = GRADES;
  protected readonly current = computed(() => this.queue()?.[0] ?? null);
  protected readonly answerHtml = computed(() => renderMarkdown(this.current()?.answer ?? ''));

  ngOnInit(): void {
    this.api.deck(this.id()).subscribe((deck) => this.deck.set(deck));
    this.api.due(this.id()).subscribe({
      next: (cards) => this.queue.set(cards),
      error: () => this.error.set('Could not load the cards. Try again.'),
    });
  }

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (!this.current() || this.grading()) return;
    const target = event.target as HTMLElement | null;
    if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
    if (!this.revealed() && (event.key === ' ' || event.key === 'Enter')) {
      event.preventDefault();
      this.reveal();
      return;
    }
    const grade = GRADES.find((g) => g.key === event.key);
    if (this.revealed() && grade) {
      event.preventDefault();
      this.grade(grade.grade);
    }
  }

  protected reveal(): void {
    this.revealed.set(true);
  }

  protected grade(grade: Grade): void {
    const card = this.current();
    if (!card) return;
    this.grading.set(true);
    this.api.review(card.id, grade).subscribe({
      next: (graded) => {
        this.grading.set(false);
        this.revealed.set(false);
        this.studied.update((ids) => new Set(ids).add(card.id));
        // Missed: back in the queue, after the others, as it's due again in a few minutes.
        this.queue.update((list) => {
          const rest = (list ?? []).slice(1);
          return grade === 'AGAIN' ? [...rest, graded] : rest;
        });
        if (grade === 'AGAIN') this.missed.update((ids) => new Set(ids).add(card.id));
      },
      error: () => {
        this.grading.set(false);
        this.error.set('Could not save that grade. Try again.');
      },
    });
  }
}
