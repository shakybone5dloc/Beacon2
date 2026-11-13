import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { DeckPage } from './deck-page';
import { DecksPage } from './decks-page';
import { StudySession } from './study-session';
import { Card, Deck, dueLabel } from './study.model';

import { aDeck } from './testing';

const PAST = '2026-01-01T00:00:00Z';
const FUTURE = '2099-01-01T00:00:00Z';

function aCard(overrides: Partial<Card> = {}): Card {
  return {
    id: 'c1',
    deckId: 'd1',
    question: 'What is OAuth?',
    answer: '- Delegated authorization',
    source: 'BRIEF',
    status: 'ACTIVE',
    dueAt: PAST,
    intervalDays: 0,
    repetitions: 0,
    lapses: 0,
    lastReviewedAt: null,
    version: 0,
    ...overrides,
  };
}

it('says when a card is next due in words', () => {
  const now = new Date('2026-11-06T12:00:00Z');
  expect(dueLabel('2026-11-06T11:00:00Z', now)).toBe('now');
  expect(dueLabel('2026-11-06T12:00:20Z', now)).toBe('now');
  expect(dueLabel('2026-11-06T12:10:00Z', now)).toBe('in 10 min');
  expect(dueLabel('2026-11-06T17:00:00Z', now)).toBe('in 5 h');
  expect(dueLabel('2026-11-07T12:00:00Z', now)).toBe('tomorrow');
  expect(dueLabel('2026-11-09T12:00:00Z', now)).toBe('in 3 days');
});

describe('study pages', () => {
  let http: HttpTestingController;

  function configure(queryParams: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  }

  afterEach(() => http.verify());

  it('lists decks with what is due and links to study them', async () => {
    configure();
    const fixture = TestBed.createComponent(DecksPage);
    fixture.detectChanges();
    http
      .expectOne('/api/study/decks')
      .flush([aDeck(), aDeck({ id: 'd2', name: 'OAuth', due: 0, drafts: 3, nextDueAt: FUTURE })]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    const decks = el.querySelectorAll('.deck');
    expect(decks[0].querySelector('.study')?.getAttribute('href')).toBe('/study/d1/session');
    expect(decks[0].querySelector('.study')?.textContent?.trim()).toBe('Study 1 due');
    expect(decks[1].querySelector('.study')).toBeNull();
    expect(decks[1].querySelector('.counts')?.textContent).toContain('3 drafts to review');
  });

  describe('DeckPage', () => {
    async function open(cards: Card[], queryParams: Record<string, string> = {}) {
      configure(queryParams);
      const fixture = TestBed.createComponent(DeckPage);
      fixture.componentRef.setInput('id', 'd1');
      fixture.detectChanges();
      http.expectOne('/api/study/decks/d1').flush(aDeck());
      http.expectOne('/api/study/decks/d1/cards').flush(cards);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('says what making cards did and keeps a draft for study', async () => {
      const { fixture, el } = await open(
        [aCard({ id: 'c1', status: 'DRAFT' }), aCard({ id: 'c2', question: 'Your pitch' })],
        { added: '3', skipped: '1' },
      );
      expect(el.querySelector('.notice')?.textContent).toContain(
        'Made 3 draft cards from the brief; 1 the deck already had were left out. Keep the ones worth drilling.',
      );
      expect(el.querySelector('.draft .answer li')?.textContent).toBe('Delegated authorization');

      el.querySelector<HTMLButtonElement>('.draft button.keep')!.click();
      http.expectOne('/api/study/cards/c1/keep').flush(aCard({ id: 'c1' }));
      await fixture.whenStable();

      expect(el.querySelector('.drafts')).toBeNull();
      expect(el.querySelectorAll('.kept-card').length).toBe(2);
    });

    it('shows every answer as a cheat sheet when asked', async () => {
      const { fixture, el } = await open([aCard()]);
      expect(el.querySelector('.kept-card .answer')).toBeNull();

      el.querySelector<HTMLInputElement>('input.cheat-sheet')!.click();
      await fixture.whenStable();

      expect(el.querySelector('.kept-card .answer li')?.textContent).toBe(
        'Delegated authorization',
      );
    });

    it('adds a card by hand', async () => {
      const { fixture, el } = await open([]);

      el.querySelector<HTMLButtonElement>('button.add')!.click();
      await fixture.whenStable();
      const question = el.querySelector<HTMLTextAreaElement>('textarea.question')!;
      question.value = 'What does PKCE protect against?';
      question.dispatchEvent(new Event('input'));
      el.querySelector('form')!.dispatchEvent(new Event('submit'));

      const req = http.expectOne(
        (r) => r.method === 'POST' && r.url === '/api/study/decks/d1/cards',
      );
      expect(req.request.body).toEqual({
        question: 'What does PKCE protect against?',
        answer: '',
        version: null,
      });
      req.flush(aCard({ id: 'c9', question: 'What does PKCE protect against?' }));
      await fixture.whenStable();
      expect(el.querySelector('.kept-card .question')?.textContent).toBe(
        'What does PKCE protect against?',
      );
    });
  });

  describe('StudySession', () => {
    async function open(cards: Card[], gradingAvailable = false) {
      configure();
      const fixture = TestBed.createComponent(StudySession);
      fixture.componentRef.setInput('id', 'd1');
      fixture.detectChanges();
      http.expectOne('/api/study/decks/d1').flush(aDeck());
      http.expectOne('/api/study/decks/d1/due').flush(cards);
      http.expectOne('/api/study/recall-grading').flush({ available: gradingAvailable });
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    beforeEach(() => localStorage.removeItem('beacon.study.mode'));
    afterEach(() => {
      delete (globalThis as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition;
      FakeRecognizer.last = null;
    });

    /** Switches to Type mode and types an answer. */
    async function typeAnswer(fixture: { whenStable(): Promise<unknown>; detectChanges(): void }, el: HTMLElement, text: string) {
      Array.from(el.querySelectorAll<HTMLButtonElement>('.modes .mode'))
        .find((b) => b.textContent?.trim() === 'Type')!
        .click();
      fixture.detectChanges();
      const box = el.querySelector<HTMLTextAreaElement>('.typed-answer')!;
      box.value = text;
      box.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      await fixture.whenStable();
      return box;
    }

    function press(key: string) {
      document.dispatchEvent(new KeyboardEvent('keydown', { key }));
    }

    it('reveals with Space and grades with the number keys', async () => {
      const { fixture, el } = await open([aCard(), aCard({ id: 'c2', question: 'Your pitch' })]);
      expect(el.querySelector('.question')?.textContent).toBe('What is OAuth?');
      expect(el.querySelector('.answer')).toBeNull();

      press('3');
      http.expectNone('/api/study/cards/c1/review');
      press(' ');
      await fixture.whenStable();
      expect(el.querySelector('.answer li')?.textContent).toBe('Delegated authorization');

      press('3');
      const req = http.expectOne('/api/study/cards/c1/review');
      expect(req.request.body).toEqual({
        grade: 'GOOD',
        mode: 'REVEAL',
        answer: null,
        suggestedGrade: null,
        feedback: null,
      });
      req.flush(aCard({ dueAt: FUTURE, intervalDays: 1 }));
      await fixture.whenStable();

      expect(el.querySelector('.question')?.textContent).toBe('Your pitch');
      expect(el.querySelector('.left')?.textContent?.trim()).toBe('1 left');
    });

    it('brings a missed card round again before finishing', async () => {
      const { fixture, el } = await open([aCard()]);

      el.querySelector<HTMLButtonElement>('button.reveal')!.click();
      await fixture.whenStable();
      el.querySelectorAll<HTMLButtonElement>('button.grade')[0].click();
      http.expectOne('/api/study/cards/c1/review').flush(aCard());
      await fixture.whenStable();
      expect(el.querySelector('.question')?.textContent).toBe('What is OAuth?');

      el.querySelector<HTMLButtonElement>('button.reveal')!.click();
      await fixture.whenStable();
      el.querySelectorAll<HTMLButtonElement>('button.grade')[3].click();
      http.expectOne('/api/study/cards/c1/review').flush(aCard({ dueAt: FUTURE }));
      await fixture.whenStable();

      expect(el.querySelector('.done .summary')?.textContent).toContain(
        '1 card studied, 1 missed and repeated',
      );
    });

    it('in Type mode, has the AI check the answer and keeps the grade the user picks', async () => {
      const { fixture, el } = await open([aCard(), aCard({ id: 'c2', question: 'Your pitch' })], true);
      const box = await typeAnswer(fixture, el, ' Letting an app act for you ');
      expect(el.querySelector('button.reveal')?.textContent).toContain('Check my answer');

      // Ctrl+Enter from the answer box checks it.
      box.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', ctrlKey: true, bubbles: true }));
      fixture.detectChanges();
      expect(el.querySelector('.checking')).not.toBeNull();
      const check = http.expectOne((r) => r.method === 'POST' && r.url === '/api/study/cards/c1/check');
      expect(check.request.body).toEqual({ answer: 'Letting an app act for you' });
      check.flush({ suggestedGrade: 'HARD', feedback: '- Missed scopes', tighter: 'OAuth lets an app act for you.' });
      fixture.detectChanges();

      expect(el.querySelector('.suggested')?.textContent).toContain('Suggested: Hard');
      expect(el.querySelector('.feedback li')?.textContent).toBe('Missed scopes');
      expect(el.querySelector('.tighter')?.textContent).toContain('OAuth lets an app act for you.');
      expect(el.querySelector('button.grade.btn-primary')?.textContent).toContain('Hard');
      expect(el.querySelector('.answer li')?.textContent).toBe('Delegated authorization');

      // The user disagrees and picks Good.
      press('3');
      const review = http.expectOne('/api/study/cards/c1/review');
      expect(review.request.body).toEqual({
        grade: 'GOOD',
        mode: 'TYPE',
        answer: 'Letting an app act for you',
        suggestedGrade: 'HARD',
        feedback: '- Missed scopes',
      });
      review.flush(aCard({ dueAt: FUTURE }));
      fixture.detectChanges();
      await fixture.whenStable();

      // The next card starts blank, and the mode is remembered.
      expect(el.querySelector('.question')?.textContent).toBe('Your pitch');
      expect(el.querySelector<HTMLTextAreaElement>('.typed-answer')!.value).toBe('');
      expect(el.querySelector('.ai-check')).toBeNull();
      expect(localStorage.getItem('beacon.study.mode')).toBe('TYPE');
    });

    it('in Type mode without a key, reveals for self-grading and says how to get suggestions', async () => {
      const { fixture, el } = await open([aCard()]);
      await typeAnswer(fixture, el, 'Delegation');
      expect(el.querySelector('button.reveal')?.textContent).toContain('Show answer');

      el.querySelector<HTMLButtonElement>('button.reveal')!.click();
      fixture.detectChanges();

      http.expectNone('/api/study/cards/c1/check');
      expect(el.querySelector('.no-key a')?.getAttribute('href')).toBe('/settings/api-keys');
      expect(el.querySelector('button.grade.btn-primary')?.textContent).toContain('Good');
      el.querySelectorAll<HTMLButtonElement>('button.grade')[1].click();
      expect(http.expectOne('/api/study/cards/c1/review').request.body).toMatchObject({
        grade: 'HARD',
        mode: 'TYPE',
        answer: 'Delegation',
        suggestedGrade: null,
      });
    });

    it('lets the user grade when the check fails', async () => {
      const { fixture, el } = await open([aCard()], true);
      await typeAnswer(fixture, el, 'Delegation');

      el.querySelector<HTMLButtonElement>('button.reveal')!.click();
      http
        .expectOne('/api/study/cards/c1/check')
        .flush({ detail: 'The AI returned a reply Beacon couldn\'t read. Try again.' }, { status: 502, statusText: 'Bad Gateway' });
      fixture.detectChanges();

      expect(el.querySelector('.check-error')?.textContent).toContain("couldn't read");
      expect(el.querySelectorAll<HTMLButtonElement>('button.grade')[2].disabled).toBe(false);
    });

    it('in Say mode, writes down what is said, lets it be fixed, and checks it like a typed answer', async () => {
      (globalThis as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition = FakeRecognizer;
      const { fixture, el } = await open([aCard()], true);
      Array.from(el.querySelectorAll<HTMLButtonElement>('.modes .mode'))
        .find((b) => b.textContent?.trim() === 'Say')!
        .click();
      fixture.detectChanges();
      expect(el.querySelector('.mic')?.textContent).toContain('Start speaking');

      el.querySelector<HTMLButtonElement>('.mic')!.click();
      fixture.detectChanges();
      const recognizer = FakeRecognizer.last!;
      expect(recognizer.started).toBe(true);
      expect(recognizer.continuous).toBe(true);
      expect(el.querySelector('.listening')).not.toBeNull();

      recognizer.say([{ text: 'it lets an app', final: true }, { text: ' act for', final: false }]);
      fixture.detectChanges();
      await fixture.whenStable();
      const box = el.querySelector<HTMLTextAreaElement>('.typed-answer')!;
      expect(box.value).toBe('it lets an app act for');
      expect(box.readOnly).toBe(true);

      el.querySelector<HTMLButtonElement>('.mic')!.click();
      recognizer.say([{ text: 'it lets an app act for you', final: true }]);
      recognizer.end();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(box.readOnly).toBe(false);
      expect(el.querySelector('.mic')?.textContent).toContain('Keep talking');

      // A misheard word fixed by hand.
      box.value = 'It lets an app act for you, with scopes';
      box.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      el.querySelector<HTMLButtonElement>('button.reveal')!.click();
      const check = http.expectOne('/api/study/cards/c1/check');
      expect(check.request.body).toEqual({ answer: 'It lets an app act for you, with scopes' });
      check.flush({ suggestedGrade: 'GOOD', feedback: '- Right', tighter: 'OAuth delegates access.' });
      fixture.detectChanges();

      press('3');
      expect(http.expectOne('/api/study/cards/c1/review').request.body).toMatchObject({
        grade: 'GOOD',
        mode: 'SAY',
        answer: 'It lets an app act for you, with scopes',
        suggestedGrade: 'GOOD',
      });
      expect(localStorage.getItem('beacon.study.mode')).toBe('SAY');
    });

    it('revealing while still listening keeps the last words', async () => {
      (globalThis as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition = FakeRecognizer;
      localStorage.setItem('beacon.study.mode', 'SAY');
      const { fixture, el } = await open([aCard()]);

      el.querySelector<HTMLButtonElement>('.mic')!.click();
      FakeRecognizer.last!.say([{ text: 'delegated', final: true }, { text: ' access', final: false }]);
      fixture.detectChanges();
      el.querySelector<HTMLButtonElement>('button.reveal')!.click();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(FakeRecognizer.last!.stopped).toBe(true);
      expect(el.querySelector<HTMLTextAreaElement>('.typed-answer')!.value).toBe('delegated access');
      el.querySelectorAll<HTMLButtonElement>('button.grade')[2].click();
      expect(http.expectOne('/api/study/cards/c1/review').request.body).toMatchObject({
        mode: 'SAY',
        answer: 'delegated access',
      });
    });

    it('explains a blocked microphone', async () => {
      (globalThis as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition = FakeRecognizer;
      localStorage.setItem('beacon.study.mode', 'SAY');
      const { fixture, el } = await open([aCard()]);

      el.querySelector<HTMLButtonElement>('.mic')!.click();
      FakeRecognizer.last!.fail('not-allowed');
      fixture.detectChanges();

      expect(el.querySelector('.speech-error')?.textContent).toContain('Microphone access is blocked');
      expect(el.querySelector('.listening')).toBeNull();
    });

    it('offers no Say mode where the browser cannot listen, falling back to Type', async () => {
      localStorage.setItem('beacon.study.mode', 'SAY');
      const { el } = await open([aCard()]);

      const say = Array.from(el.querySelectorAll<HTMLButtonElement>('.modes .mode')).find(
        (b) => b.textContent?.trim() === 'Say',
      )!;
      expect(say.disabled).toBe(true);
      expect(say.title).toContain('Chrome and Edge');
      expect(el.querySelector('.modes .mode.btn-primary')?.textContent?.trim()).toBe('Type');
      expect(el.querySelector('.mic')).toBeNull();
    });

    it('says when nothing is due', async () => {
      const { el } = await open([]);

      expect(el.querySelector('.done h1')?.textContent).toBe('Nothing due right now');
    });
  });
});

/** Stands in for the browser's speech recognition, driven by the test. */
class FakeRecognizer {
  static last: FakeRecognizer | null = null;
  lang = '';
  continuous = false;
  interimResults = false;
  started = false;
  stopped = false;
  onresult: ((event: unknown) => void) | null = null;
  onerror: ((event: { error: string }) => void) | null = null;
  onend: (() => void) | null = null;

  constructor() {
    FakeRecognizer.last = this;
  }

  start() {
    this.started = true;
  }

  stop() {
    this.stopped = true;
    this.end();
  }

  abort() {
    this.stopped = true;
  }

  /** Everything recognized so far in this run, as the browser reports it each time. */
  say(results: { text: string; final: boolean }[]) {
    this.onresult?.({
      resultIndex: 0,
      results: results.map((r) => ({ isFinal: r.final, 0: { transcript: r.text } })),
    });
  }

  fail(error: string) {
    this.onerror?.({ error });
    this.end();
  }

  end() {
    this.onend?.();
  }
}
