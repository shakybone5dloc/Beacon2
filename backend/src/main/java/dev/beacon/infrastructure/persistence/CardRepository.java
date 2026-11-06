package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.study.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findAllByDeckIdOrderByCreatedAtAsc(UUID deckId);

    Optional<Card> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Cards kept for study and due by {@code now}, most overdue first. */
    @Query("""
            select c from Card c
            where c.deckId = :deckId and c.status = dev.beacon.domain.study.Card.Status.ACTIVE and c.dueAt <= :now
            order by c.dueAt
            """)
    List<Card> findDue(UUID deckId, Instant now);

    /**
     * Per deck: [deckId, all cards, due now, drafts, earliest due date of a kept card], in one
     * query for the whole deck list.
     */
    @Query("""
            select c.deckId, count(c),
                   sum(case when c.status = dev.beacon.domain.study.Card.Status.ACTIVE and c.dueAt <= :now then 1 else 0 end),
                   sum(case when c.status = dev.beacon.domain.study.Card.Status.DRAFT then 1 else 0 end),
                   min(case when c.status = dev.beacon.domain.study.Card.Status.ACTIVE then c.dueAt end)
            from Card c
            where c.ownerId = :ownerId
            group by c.deckId
            """)
    List<Object[]> countsByDeck(UUID ownerId, Instant now);
}
