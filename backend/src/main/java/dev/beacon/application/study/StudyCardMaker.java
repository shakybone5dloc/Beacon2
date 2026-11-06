package dev.beacon.application.study;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.stories.StoryService;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.study.MadeCardsResponse;
import dev.beacon.domain.briefs.BriefSection;
import dev.beacon.domain.briefs.SectionKind;
import dev.beacon.domain.stories.Story;
import dev.beacon.domain.study.Card;
import dev.beacon.domain.study.Deck;
import dev.beacon.infrastructure.persistence.BriefSectionRepository;
import dev.beacon.infrastructure.persistence.CardRepository;
import dev.beacon.infrastructure.persistence.DeckRepository;
import dev.beacon.infrastructure.persistence.InterviewBriefRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Makes draft study cards from an application's interview brief (A1): the AI turns its
 * questions, refresher, gaps and pitch into cards, and each story the brief picked from the
 * story bank (A7) becomes a "tell the story" card. They land in the application's deck as
 * drafts for the user to keep or drop; a question the deck already has is skipped.
 */
@Service
public class StudyCardMaker {

    /** The brief's sections worth drilling, in the order their cards are made. */
    private static final List<SectionKind> SOURCES =
            List.of(SectionKind.QUESTIONS, SectionKind.REFRESHER, SectionKind.GAP, SectionKind.PITCH);

    /** Tried in this order: a user with both keys gets OpenAI, as everywhere else. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private final JobApplicationRepository applications;
    private final InterviewBriefRepository briefs;
    private final BriefSectionRepository sections;
    private final DeckRepository decks;
    private final CardRepository cards;
    private final StoryService stories;
    private final StudyService study;
    private final List<CardWriter> writers;
    private final UserCredentials credentials;
    private final AiUsageService usage;
    private final TransactionTemplate tx;
    private final Clock clock;

    public StudyCardMaker(JobApplicationRepository applications, InterviewBriefRepository briefs,
                          BriefSectionRepository sections, DeckRepository decks, CardRepository cards,
                          StoryService stories, StudyService study, List<CardWriter> writers,
                          UserCredentials credentials, AiUsageService usage, PlatformTransactionManager transactions,
                          Clock clock) {
        this.applications = applications;
        this.briefs = briefs;
        this.sections = sections;
        this.decks = decks;
        this.cards = cards;
        this.stories = stories;
        this.study = study;
        this.writers = writers;
        this.credentials = credentials;
        this.usage = usage;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    private record Gathered(String company, String employer, String role, List<CardWriter.Section> written,
                            List<Story> pickedStories) {}

    /** Empty when the application does not exist. */
    public Optional<MadeCardsResponse> fromBrief(UUID ownerId, UUID applicationId) {
        var gathered = tx.execute(status -> gather(ownerId, applicationId));
        if (gathered.isEmpty()) return Optional.empty();
        var g = gathered.get();

        // The AI call is made outside any transaction, so it holds no database connection.
        var drafts = new ArrayList<Pending>();
        if (!g.written().isEmpty()) {
            var provider = PREFERENCE.stream()
                    .filter(credentials.savedProviders(ownerId)::contains)
                    .filter(id -> writers.stream().anyMatch(w -> w.provider().equals(id)))
                    .findFirst()
                    .orElseThrow(() -> new StudyException("Add an OpenAI or Anthropic API key under Settings to make study cards."));
            String apiKey;
            try {
                apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
            } catch (KeyCipher.UnreadableKeyException ex) {
                throw new StudyException(ex.getMessage());
            }
            var writer = writers.stream().filter(w -> w.provider().equals(provider)).findFirst().orElseThrow();
            writer.write(new CardWriter.CardInput(g.company(), g.role(), g.written()), apiKey,
                            call -> usage.record(ownerId, AiFeature.STUDY_CARDS, call))
                    .forEach(d -> drafts.add(new Pending(d.question(), d.answer(), Card.Source.BRIEF, null)));
        }
        g.pickedStories().forEach(story -> drafts.add(storyCard(story)));

        return Optional.of(tx.execute(status -> save(ownerId, applicationId, g, drafts)));
    }

    private Optional<Gathered> gather(UUID ownerId, UUID applicationId) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();

        var stored = briefs.findByApplicationIdAndOwnerId(applicationId, ownerId)
                .map(brief -> sections.findAllByBriefId(brief.getId()))
                .orElse(List.of());
        var written = SOURCES.stream()
                .flatMap(kind -> stored.stream().filter(s -> s.getKind() == kind && hasText(s.getContent())))
                .map(s -> new CardWriter.Section(s.getKind().title(), s.getContent()))
                .toList();
        var storiesSection = stored.stream()
                .filter(s -> s.getKind() == SectionKind.STORIES)
                .map(BriefSection::getContent)
                .findFirst()
                .orElse("");
        var picked = stories.all(ownerId).stream()
                .filter(story -> storiesSection.contains(story.getTitle()))
                .toList();
        if (written.isEmpty() && picked.isEmpty()) {
            throw new StudyException("Write the brief's likely questions or technical refresher first; "
                    + "the cards are made from them.");
        }
        return Optional.of(new Gathered(application.getCompanyName(), application.getEmployerLabel(),
                application.getRole(), written, picked));
    }

    private record Pending(String question, String answer, Card.Source source, UUID storyId) {}

    private MadeCardsResponse save(UUID ownerId, UUID applicationId, Gathered g, List<Pending> drafts) {
        var now = Instant.now(clock);
        var deck = decks.findByApplicationIdAndOwnerId(applicationId, ownerId)
                .orElseGet(() -> decks.saveAndFlush(Deck.create(ownerId, g.employer() + " – " + g.role(), applicationId, now)));

        var asked = cards.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()).stream()
                .map(card -> normalize(card.getQuestion()))
                .collect(Collectors.toCollection(HashSet::new));
        int added = 0;
        int skipped = 0;
        for (var draft : drafts) {
            if (draft.question() == null || draft.question().isBlank() || !asked.add(normalize(draft.question()))) {
                skipped++;
                continue;
            }
            cards.save(Card.create(ownerId, deck.getId(), draft.question(), draft.answer(), draft.source(),
                    draft.storyId(), Card.Status.DRAFT, now));
            added++;
        }
        deck.touch(now);
        cards.flush();
        return new MadeCardsResponse(study.deck(ownerId, deck.getId()).orElseThrow(), added, skipped);
    }

    /** A story from the bank as a card: its title to prompt, its STAR outline to check against. */
    static Pending storyCard(Story story) {
        var answer = new StringBuilder();
        line(answer, "Situation", story.getSituation());
        line(answer, "Task", story.getTask());
        line(answer, "Action", story.getAction());
        line(answer, "Result", story.getResult());
        line(answer, "Numbers", story.getMetrics());
        var tags = story.getTags().isEmpty() ? "" : " (" + String.join(", ", story.getTags()) + ")";
        return new Pending("Tell the story: " + story.getTitle() + tags, answer.toString().strip(),
                Card.Source.STORY, story.getId());
    }

    private static void line(StringBuilder text, String label, String value) {
        if (hasText(value)) text.append("**").append(label).append(":** ").append(value.strip()).append("\n\n");
    }

    /** "What is OAuth?" and "what is  oauth" are the same question. */
    static String normalize(String question) {
        return question.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
