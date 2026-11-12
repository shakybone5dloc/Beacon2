package dev.beacon.application.study;

import dev.beacon.contracts.study.CardResponse;
import dev.beacon.contracts.study.DeckResponse;
import dev.beacon.domain.interviews.InterviewOutcome;
import dev.beacon.domain.study.Card;
import dev.beacon.domain.study.Deck;
import dev.beacon.domain.study.Grade;
import dev.beacon.infrastructure.persistence.CardRepository;
import dev.beacon.infrastructure.persistence.DeckRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.persistence.ReviewRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Study decks and cards (A6): keeping them, and grading them as they're studied. */
@Service
public class StudyService {

    private final DeckRepository decks;
    private final CardRepository cards;
    private final ReviewRepository reviews;
    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final Clock clock;

    public StudyService(DeckRepository decks, CardRepository cards, ReviewRepository reviews,
                        JobApplicationRepository applications, InterviewRepository interviews, Clock clock) {
        this.interviews = interviews;
        this.decks = decks;
        this.cards = cards;
        this.reviews = reviews;
        this.applications = applications;
        this.clock = clock;
    }

    /** Every deck with its counts, most recently used first. */
    @Transactional(readOnly = true)
    public List<DeckResponse> decks(UUID ownerId) {
        var now = Instant.now(clock);
        var counts = new HashMap<UUID, Object[]>();
        cards.countsByDeck(ownerId, now).forEach(row -> counts.put((UUID) row[0], row));
        return decks.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(deck -> toResponse(ownerId, deck, counts.get(deck.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DeckResponse> deck(UUID ownerId, UUID deckId) {
        return decks.findByIdAndOwnerId(deckId, ownerId).map(this::withCounts);
    }

    @Transactional
    public DeckResponse createDeck(UUID ownerId, String name) {
        return withCounts(decks.saveAndFlush(Deck.create(ownerId, name, null, Instant.now(clock))));
    }

    @Transactional
    public Optional<DeckResponse> renameDeck(UUID ownerId, UUID deckId, String name, Long version) {
        return decks.findByIdAndOwnerId(deckId, ownerId).map(deck -> {
            requireVersion(deck.getVersion(), version, "This deck");
            deck.rename(name, Instant.now(clock));
            return withCounts(decks.saveAndFlush(deck));
        });
    }

    @Transactional
    public boolean deleteDeck(UUID ownerId, UUID deckId) {
        var deck = decks.findByIdAndOwnerId(deckId, ownerId);
        deck.ifPresent(decks::delete);
        return deck.isPresent();
    }

    /** All of a deck's cards, drafts included, oldest first. Empty when the deck doesn't exist. */
    @Transactional(readOnly = true)
    public Optional<List<CardResponse>> cards(UUID ownerId, UUID deckId) {
        return decks.findByIdAndOwnerId(deckId, ownerId)
                .map(deck -> cards.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()).stream()
                        .map(StudyService::toResponse).toList());
    }

    /** Kept cards due now, most overdue first. Empty when the deck doesn't exist. */
    @Transactional(readOnly = true)
    public Optional<List<CardResponse>> due(UUID ownerId, UUID deckId) {
        return decks.findByIdAndOwnerId(deckId, ownerId)
                .map(deck -> cards.findDue(deck.getId(), Instant.now(clock)).stream()
                        .map(StudyService::toResponse).toList());
    }

    /** A card written by hand, kept straight away. Empty when the deck doesn't exist. */
    @Transactional
    public Optional<CardResponse> addCard(UUID ownerId, UUID deckId, String question, String answer) {
        return decks.findByIdAndOwnerId(deckId, ownerId).map(deck -> {
            var now = Instant.now(clock);
            deck.touch(now);
            return toResponse(cards.saveAndFlush(Card.create(ownerId, deck.getId(), question, answer,
                    Card.Source.MANUAL, null, Card.Status.ACTIVE, now)));
        });
    }

    @Transactional
    public Optional<CardResponse> editCard(UUID ownerId, UUID cardId, String question, String answer, Long version) {
        return cards.findByIdAndOwnerId(cardId, ownerId).map(card -> {
            requireVersion(card.getVersion(), version, "This card");
            card.edit(question, answer, Instant.now(clock));
            return toResponse(cards.saveAndFlush(card));
        });
    }

    @Transactional
    public boolean deleteCard(UUID ownerId, UUID cardId) {
        var card = cards.findByIdAndOwnerId(cardId, ownerId);
        card.ifPresent(cards::delete);
        return card.isPresent();
    }

    /** Keeps a draft for study; it's due straight away. */
    @Transactional
    public Optional<CardResponse> keep(UUID ownerId, UUID cardId) {
        return cards.findByIdAndOwnerId(cardId, ownerId).map(card -> {
            card.keep(Instant.now(clock));
            return toResponse(cards.saveAndFlush(card));
        });
    }

    /** Keeps every draft in the deck. Empty when the deck doesn't exist. */
    @Transactional
    public Optional<DeckResponse> keepAll(UUID ownerId, UUID deckId) {
        return decks.findByIdAndOwnerId(deckId, ownerId).map(deck -> {
            var now = Instant.now(clock);
            cards.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()).forEach(card -> card.keep(now));
            cards.flush();
            return withCounts(deck);
        });
    }

    /** Grades a kept card and schedules its next review. Empty when the card doesn't exist. */
    @Transactional
    public Optional<CardResponse> review(UUID ownerId, UUID cardId, Grade grade) {
        return cards.findByIdAndOwnerId(cardId, ownerId).map(card -> {
            if (card.getStatus() != Card.Status.ACTIVE) {
                throw new StudyException("Keep this draft before studying it.");
            }
            var now = Instant.now(clock);
            var deck = decks.findById(card.getDeckId()).orElseThrow();
            reviews.save(card.review(grade, now, nextInterview(deck, now)));
            deck.touch(now);
            return toResponse(cards.saveAndFlush(card));
        });
    }

    /** When the deck's application next has an interview, or null when it has none ahead. */
    private Instant nextInterview(Deck deck, Instant now) {
        if (deck.getApplicationId() == null) return null;
        return interviews.findNextAndLast(List.of(deck.getApplicationId()), now, InterviewOutcome.CANCELLED).stream()
                .findFirst()
                .map(row -> (Instant) row[1])
                .orElse(null);
    }

    private static void requireVersion(Long current, Long given, String what) {
        if (!Objects.equals(current, given)) {
            throw new OptimisticLockingFailureException(what + " was changed somewhere else.");
        }
    }

    private DeckResponse withCounts(Deck deck) {
        var row = cards.countsByDeck(deck.getOwnerId(), Instant.now(clock)).stream()
                .filter(r -> deck.getId().equals(r[0]))
                .findFirst()
                .orElse(null);
        return toResponse(deck.getOwnerId(), deck, row);
    }

    DeckResponse toResponse(UUID ownerId, Deck deck, Object[] counts) {
        var label = deck.getApplicationId() == null ? null : applications.findByIdAndOwnerId(deck.getApplicationId(), ownerId)
                .map(a -> a.getEmployerLabel() + " – " + a.getRole())
                .orElse(null);
        return new DeckResponse(deck.getId(), deck.getName(), deck.getKind(), deck.getApplicationId(), label,
                counts == null ? 0 : ((Number) counts[1]).longValue(),
                counts == null || counts[2] == null ? 0 : ((Number) counts[2]).longValue(),
                counts == null || counts[3] == null ? 0 : ((Number) counts[3]).longValue(),
                counts == null ? null : instant(counts[4]),
                deck.getVersion(), deck.getCreatedAt(), deck.getUpdatedAt());
    }

    /** A timestamp from a native query, whichever type the driver hands back. */
    static Instant instant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant i -> i;
            case java.time.OffsetDateTime o -> o.toInstant();
            case java.sql.Timestamp t -> t.toInstant();
            default -> throw new IllegalStateException("Not a timestamp: " + value.getClass());
        };
    }

    static CardResponse toResponse(Card c) {
        return new CardResponse(c.getId(), c.getDeckId(), c.getQuestion(), c.getAnswer(), c.getSource(), c.getStatus(),
                c.getDueAt(), c.getIntervalDays(), c.getRepetitions(), c.getLapses(), c.getLastReviewedAt(),
                c.getVersion());
    }
}
