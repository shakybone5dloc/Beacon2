package dev.beacon.domain.study;

/** How a card was answered before its answer was shown. */
public enum AnswerMode {
    /** In the user's head or out loud, then revealed: recognition. */
    REVEAL,
    /** Typed first, then compared: producing it. */
    TYPE,
    /** Spoken first and turned into text by the browser. */
    SAY
}
