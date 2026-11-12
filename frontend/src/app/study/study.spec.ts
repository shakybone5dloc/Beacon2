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
    async function open(cards: Card[]) {
      configure();
      const fixture = TestBed.createComponent(StudySession);
      fixture.componentRef.setInput('id', 'd1');
      fixture.detectChanges();
      http.expectOne('/api/study/decks/d1').flush(aDeck());
      http.expectOne('/api/study/decks/d1/due').flush(cards);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
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
      expect(req.request.body).toEqual({ grade: 'GOOD' });
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

    it('says when nothing is due', async () => {
      const { el } = await open([]);

      expect(el.querySelector('.done h1')?.textContent).toBe('Nothing due right now');
    });
  });
});
