import {
  Component,
  computed,
  effect,
  HostListener,
  inject,
  input,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { renderMarkdown } from '../briefs/brief.model';
import { problemText } from '../documents/application-documents';
import { SpeechInput } from './speech-input';
import { StudyApi } from './study-api';
import {
  ANSWER_MODES,
  AnswerCheck,
  AnswerMode,
  Card,
  Deck,
  Grade,
  GRADES,
} from './study.model';

/** Remembers the last mode picked, so a Type habit sticks between sessions. */
const MODE_KEY = 'beacon.study.mode';

/**
 * Studying a deck's due cards. In Reveal mode: read the question, answer out loud, reveal,
 * grade. In Type mode: write the answer first; on reveal the AI (with the user's key) compares
 * it with the card and suggests a grade and a tighter way to say it. Say mode is Type mode
 * spoken: the browser turns speech into text, which can be fixed before it's checked. The user confirms or picks
 * another grade, and only that grade schedules the card. A card graded Again goes back in the
 * queue and comes round again before the session ends.
 * Space or Enter reveals; Ctrl+Enter reveals from the answer box; 1 to 4 grade.
 */
@Component({
  selector: 'app-study-session',
  imports: [FormsModule, RouterLink],
  templateUrl: './study-session.html',
  providers: [SpeechInput],
  host: { class: 'block max-w-2xl mx-auto' },
})
export class StudySession implements OnInit {
  private readonly api = inject(StudyApi);
  protected readonly speech = inject(SpeechInput);

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

  protected readonly mode = signal<AnswerMode>(savedMode(this.speech.supported));
  /** What the user typed or said for the current card, in Type and Say mode. */
  protected readonly typed = signal('');
  /** Whether the user has a key, so typed answers can be checked. */
  protected readonly canCheck = signal(false);
  protected readonly checking = signal(false);
  protected readonly check = signal<AnswerCheck | null>(null);
  protected readonly checkError = signal<string | null>(null);

  protected readonly grades = GRADES;
  protected readonly modes = ANSWER_MODES;
  protected readonly current = computed(() => this.queue()?.[0] ?? null);
  protected readonly answerHtml = computed(() => renderMarkdown(this.current()?.answer ?? ''));
  protected readonly feedbackHtml = computed(() => renderMarkdown(this.check()?.feedback ?? ''));
  /** The grade to highlight: the AI's suggestion when there is one, otherwise Good. */
  protected readonly highlighted = computed<Grade>(() => this.check()?.suggestedGrade ?? 'GOOD');
  /** Type and Say both write an answer before the reveal. */
  protected readonly writesAnswer = computed(() => this.mode() !== 'REVEAL');
  /** The answer box: what's been said, plus the words still being recognized. */
  protected readonly shownAnswer = computed(() => {
    const pending = this.speech.interim();
    return pending ? `${this.typed()} ${pending}`.trim() : this.typed();
  });

  constructor() {
    // What's been said lands in the answer box, where it can be fixed by hand.
    effect(() => {
      const said = this.speech.text();
      untracked(() => {
        if (this.mode() === 'SAY') this.typed.set(said);
      });
    });
  }

  ngOnInit(): void {
    this.api.deck(this.id()).subscribe((deck) => this.deck.set(deck));
    this.api.due(this.id()).subscribe({
      next: (cards) => this.queue.set(cards),
      error: () => this.error.set('Could not load the cards. Try again.'),
    });
    // Without it, Type mode still works with self-grading.
    this.api.gradingAvailable().subscribe({
      next: (available) => this.canCheck.set(available),
      error: () => this.canCheck.set(false),
    });
  }

  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    if (!this.current() || this.grading()) return;
    // A key pressed with nothing focused comes from the document, which isn't an element.
    const target = event.target instanceof HTMLElement ? event.target : null;
    const inAnswerBox = target?.classList.contains('typed-answer') ?? false;
    if (inAnswerBox && !this.revealed() && event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      this.reveal();
      return;
    }
    if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
    if (!this.revealed() && (event.key === ' ' || event.key === 'Enter')) {
      event.preventDefault();
      this.reveal();
      return;
    }
    const grade = GRADES.find((g) => g.key === event.key);
    if (this.revealed() && grade && !this.checking()) {
      event.preventDefault();
      this.grade(grade.grade);
    }
  }

  protected setMode(mode: AnswerMode): void {
    if (mode === 'SAY' && !this.speech.supported) return;
    if (this.mode() === 'SAY') this.speech.stop();
    this.mode.set(mode);
    try {
      localStorage.setItem(MODE_KEY, mode);
    } catch {
      // Private windows may refuse storage; the mode just isn't remembered.
    }
  }

  /** Starts or stops listening; speaking again adds to what's already there. */
  protected toggleMic(event: Event): void {
    // Leave the button, so Space reveals instead of pressing it again.
    (event.currentTarget as HTMLElement | null)?.blur?.();
    if (this.speech.listening()) {
      this.speech.stop();
    } else {
      this.speech.text.set(this.typed());
      this.speech.start();
    }
  }

  /** Shows the answer; in Type or Say mode with an answer, also asks the AI for a grade. */
  protected reveal(): void {
    if (this.speech.listening()) {
      // Words still being recognized count as said.
      this.typed.set(this.shownAnswer());
      this.speech.stop();
    }
    this.revealed.set(true);
    // Leave the answer box so 1 to 4 grade straight away.
    (document.activeElement as HTMLElement | null)?.blur?.();
    const card = this.current();
    const answer = this.typed().trim();
    if (!card || !this.writesAnswer() || !answer || !this.canCheck()) return;
    this.checking.set(true);
    this.api.check(card.id, answer).subscribe({
      next: (check) => {
        // Ignore a reply for a card already graded.
        if (this.current()?.id === card.id) this.check.set(check);
        this.checking.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.checking.set(false);
        this.checkError.set(problemText(err, 'Could not check the answer. Grade it yourself.'));
      },
    });
  }

  protected grade(grade: Grade): void {
    const card = this.current();
    if (!card) return;
    this.grading.set(true);
    const mode = this.mode();
    const answer =
      mode === 'REVEAL'
        ? { mode }
        : { mode, text: this.typed().trim(), check: this.check() };
    this.api.review(card.id, grade, answer).subscribe({
      next: (graded) => {
        this.grading.set(false);
        this.revealed.set(false);
        this.typed.set('');
        this.speech.reset();
        this.check.set(null);
        this.checkError.set(null);
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

  protected gradeLabel(grade: Grade): string {
    return GRADES.find((g) => g.grade === grade)?.label ?? grade;
  }
}

/** The mode picked last time; Say falls back to Type where the browser can't listen. */
function savedMode(canListen: boolean): AnswerMode {
  try {
    const saved = localStorage.getItem(MODE_KEY);
    if (saved === 'SAY') return canListen ? 'SAY' : 'TYPE';
    return saved === 'TYPE' ? 'TYPE' : 'REVEAL';
  } catch {
    return 'REVEAL';
  }
}
