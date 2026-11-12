package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.study.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findAllByDeckIdOrderByCreatedAtAsc(UUID deckId);

    Optional<Card> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Cards made from a story, in any deck, so they can follow it when it's edited. */
    List<Card> findAllByStoryId(UUID storyId);

    /**
     * When a kept card counts as due, in SQL over {@code c} (a card): its date has come, or its
     * deck's application has an interview within the next day ({@link
     * dev.beacon.domain.study.Scheduler#FINAL_LOOK}) and the card hasn't been seen since that
     * last day began, so every card gets one more look before the interview.
     */
    String DUE = """
            c.status = 'ACTIVE' and (c.due_at <= :now or exists (
                select 1 from study_decks d join interviews i on i.application_id = d.application_id
                where d.id = c.deck_id
                  and (i.outcome is null or i.outcome <> 'CANCELLED')
                  and i.starts_at > :now and i.starts_at - interval '1 day' <= :now
                  and (c.last_reviewed_at is null or c.last_reviewed_at < i.starts_at - interval '1 day')))
            """;

    /** Kept cards due by {@code now}, most overdue first. */
    @Query(nativeQuery = true, value = "select c.* from study_cards c where c.deck_id = :deckId and " + DUE
            + " order by c.due_at")
    List<Card> findDue(UUID deckId, Instant now);

    /**
     * Per deck: [deckId, all cards, due now, drafts, earliest due date of a kept card], in one
     * query for the whole deck list.
     */
    @Query(nativeQuery = true, value = """
            select c.deck_id, count(*),
                   count(*) filter (where\s""" + DUE + """
            ),
                   count(*) filter (where c.status = 'DRAFT'),
                   min(c.due_at) filter (where c.status = 'ACTIVE')
            from study_cards c
            where c.owner_id = :ownerId
            group by c.deck_id
            """)
    List<Object[]> countsByDeck(UUID ownerId, Instant now);

    /** Per application with a deck: [application id, kept cards due now]; only those with some due. */
    @Query(nativeQuery = true, value = """
            select d.application_id, count(*)
            from study_cards c join study_decks d on d.id = c.deck_id
            where d.application_id in (:applicationIds) and\s""" + DUE + """
            group by d.application_id
            """)
    List<Object[]> dueByApplication(Collection<UUID> applicationIds, Instant now);
}
