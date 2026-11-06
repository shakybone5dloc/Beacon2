package dev.beacon.contracts.study;

/**
 * What making cards from a brief did.
 *
 * @param added   new draft cards in the deck
 * @param skipped cards left out because the deck already asks the same question
 */
public record MadeCardsResponse(DeckResponse deck, int added, int skipped) {}
