import { DestroyRef, inject, Injectable, signal } from '@angular/core';

/**
 * The parts of the browser's Web Speech API this uses. Chrome and Edge ship it as
 * `webkitSpeechRecognition`; Firefox has none, so speaking isn't offered there.
 */
interface Recognizer {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: RecognitionResultEvent) => void) | null;
  onerror: ((event: { error: string }) => void) | null;
  onend: (() => void) | null;
  start(): void;
  stop(): void;
  abort(): void;
}

interface RecognitionResultEvent {
  resultIndex: number;
  results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }>;
}

type RecognizerConstructor = new () => Recognizer;

function recognizerConstructor(): RecognizerConstructor | null {
  const w = globalThis as unknown as {
    SpeechRecognition?: RecognizerConstructor;
    webkitSpeechRecognition?: RecognizerConstructor;
  };
  return w.SpeechRecognition ?? w.webkitSpeechRecognition ?? null;
}

/** What went wrong, in words for the user. */
const ERRORS: Record<string, string> = {
  'not-allowed': 'Microphone access is blocked. Allow it for this site in the browser, then try again.',
  'service-not-allowed': 'Microphone access is blocked. Allow it for this site in the browser, then try again.',
  'audio-capture': 'No microphone was found.',
  'no-speech': "Didn't hear anything. Try again, a little closer to the microphone.",
  network: "The browser couldn't reach its speech service. Check the connection, or type instead.",
};

/**
 * Speech to text for answering a card out loud (A8 Say mode), using the browser's own speech
 * recognition: no key and no cost. `text` holds what has been said so far; `interim` the words
 * still being recognized. Provided per component, so each session has its own.
 */
@Injectable()
export class SpeechInput {
  private readonly create = recognizerConstructor();
  private recognizer: Recognizer | null = null;
  /** Text already settled before this listening run started, kept when listening again. */
  private before = '';

  /** False in browsers without speech recognition. */
  readonly supported = this.create != null;
  readonly listening = signal(false);
  readonly text = signal('');
  readonly interim = signal('');
  readonly error = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.recognizer?.abort());
  }

  /** Starts listening, adding to whatever `text` already holds. */
  start(): void {
    if (!this.create || this.listening()) return;
    const recognizer = new this.create();
    recognizer.lang = navigator.language || 'en-US';
    recognizer.continuous = true;
    recognizer.interimResults = true;
    this.before = this.text().trim();
    this.error.set(null);
    this.interim.set('');

    recognizer.onresult = (event) => {
      let settled = '';
      let pending = '';
      for (let i = 0; i < event.results.length; i++) {
        const result = event.results[i];
        if (result.isFinal) settled += result[0].transcript;
        else pending += result[0].transcript;
      }
      this.text.set(join(this.before, settled));
      this.interim.set(pending.trim());
    };
    recognizer.onerror = (event) => {
      // Stopping on purpose reports "aborted"; that's not a problem to show.
      if (event.error !== 'aborted') {
        this.error.set(ERRORS[event.error] ?? 'Speech recognition stopped. Try again, or type instead.');
      }
    };
    recognizer.onend = () => {
      // Words still pending when it stopped are kept rather than lost.
      if (this.interim()) this.text.set(join(this.text(), this.interim()));
      this.interim.set('');
      this.listening.set(false);
      this.recognizer = null;
    };

    this.recognizer = recognizer;
    this.listening.set(true);
    try {
      recognizer.start();
    } catch {
      this.listening.set(false);
      this.recognizer = null;
      this.error.set('Speech recognition could not start. Try again, or type instead.');
    }
  }

  /** Stops listening; the last words arrive before `listening` turns false. */
  stop(): void {
    this.recognizer?.stop();
  }

  /** Clears the answer for the next card, stopping first if needed. */
  reset(): void {
    this.recognizer?.abort();
    this.recognizer = null;
    this.listening.set(false);
    this.text.set('');
    this.interim.set('');
    this.error.set(null);
  }
}

function join(a: string, b: string): string {
  return [a.trim(), b.trim()].filter(Boolean).join(' ');
}
