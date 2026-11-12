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
import dev.beacon.domain.study.StoryCard;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.fit.FitScore;
import dev.beacon.domain.interviews.Interview;
import dev.beacon.infrastructure.persistence.ApplicationFitRepository;
import dev.beacon.infrastructure.persistence.BriefSectionRepository;
import dev.beacon.infrastructure.persistence.FitScoreRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
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
import java.util.function.Supplier;
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
    private final InterviewRepository interviews;
    private final ApplicationFitRepository fits;
    private final FitScoreRepository fitScores;
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

    public StudyCardMaker(JobApplicationRepository applications, InterviewRepository interviews,
                          ApplicationFitRepository fits, FitScoreRepository fitScores, InterviewBriefRepository briefs,
                          BriefSectionRepository sections, DeckRepository decks, CardRepository cards,
                          StoryService stories, StudyService study, List<CardWriter> writers,
                          UserCredentials credentials, AiUsageService usage, PlatformTransactionManager transactions,
                          Clock clock) {
        this.applications = applications;
        this.interviews = interviews;
        this.fits = fits;
        this.fitScores = fitScores;
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

    private record Gathered(UUID applicationId, String company, String employer, String role, List<CardWriter.Section> written,
                            List<Story> pickedStories) {}

    /** Empty when the application does not exist. */
    public Optional<MadeCardsResponse> fromBrief(UUID ownerId, UUID applicationId) {
        return make(ownerId, () -> applications.findByIdAndOwnerId(applicationId, ownerId)
                .map(application -> gather(ownerId, application, null)));
    }

    /**
     * "Study for this interview": cards from the application's brief plus this interview's prep
     * notes, or from the posting and the fit check's gaps when there's no brief to go on. They
     * land in the application's deck. Empty when the interview does not exist.
     */
    public Optional<MadeCardsResponse> fromInterview(UUID ownerId, UUID interviewId) {
        return make(ownerId, () -> interviews.findByIdAndOwnerId(interviewId, ownerId)
                .map(interview -> gather(ownerId, interview.getApplication(), interview)));
    }

    private Optional<MadeCardsResponse> make(UUID ownerId, Supplier<Optional<Gathered>> gatherer) {
        var gathered = tx.execute(status -> gatherer.get());
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

        return Optional.of(tx.execute(status -> save(ownerId, g, drafts)));
    }

    /** How much of a posting goes to the AI: the requirements, without paying for pages of boilerplate. */
    static final int POSTING_LIMIT = 8_000;

    private Gathered gather(UUID ownerId, JobApplication application, Interview interview) {
        var applicationId = application.getId();
        var stored = briefs.findByApplicationIdAndOwnerId(applicationId, ownerId)
                .map(brief -> sections.findAllByBriefId(brief.getId()))
                .orElse(List.of());
        var written = new ArrayList<>(SOURCES.stream()
                .flatMap(kind -> stored.stream().filter(s -> s.getKind() == kind && hasText(s.getContent())))
                .map(s -> new CardWriter.Section(s.getKind().title(), s.getContent()))
                .toList());
        if (interview != null) {
            var fromBrief = !written.isEmpty();
            if (hasText(interview.getPrepNotes())) {
                written.add(new CardWriter.Section("Prep notes for this interview", interview.getPrepNotes()));
            }
            if (!fromBrief) {
                fits.findById(applicationId)
                        .flatMap(link -> fitScores.findByIdAndOwnerId(link.getFitScoreId(), ownerId))
                        .map(FitScore::getGaps)
                        .filter(gaps -> gaps != null && !gaps.isEmpty())
                        .ifPresent(gaps -> written.add(new CardWriter.Section("Gaps from the fit check",
                                gaps.stream().map(gap -> "- " + gap).collect(Collectors.joining("\n")))));
                if (hasText(application.getJobDescription())) {
                    var posting = application.getJobDescription().strip();
                    written.add(new CardWriter.Section("Job posting",
                            posting.length() > POSTING_LIMIT ? posting.substring(0, POSTING_LIMIT) : posting));
                }
            }
        }
        var storiesSection = stored.stream()
                .filter(s -> s.getKind() == SectionKind.STORIES)
                .map(BriefSection::getContent)
                .findFirst()
                .orElse("");
        var picked = stories.all(ownerId).stream()
                .filter(story -> storiesSection.contains(story.getTitle()))
                .toList();
        if (written.isEmpty() && picked.isEmpty()) {
            throw new StudyException(interview == null
                    ? "Write the brief's likely questions or technical refresher first; the cards are made from them."
                    : "Add prep notes to this interview, write its brief, or paste the job posting first; "
                            + "the cards are made from them.");
        }
        return new Gathered(applicationId, application.getCompanyName(), application.getEmployerLabel(),
                application.getRole(), List.copyOf(written), picked);
    }

    private record Pending(String question, String answer, Card.Source source, UUID storyId) {}

    private MadeCardsResponse save(UUID ownerId, Gathered g, List<Pending> drafts) {
        var applicationId = g.applicationId();
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

    /** A story from the bank as a card. */
    static Pending storyCard(Story story) {
        return new Pending(StoryCard.question(story), StoryCard.answer(story), Card.Source.STORY, story.getId());
    }

    /**
     * Drills stories from the bank: a "Tell the story" card for each, kept straight away (they're
     * the user's own words, so there's nothing to review), in the user's Stories deck. A story
     * already in that deck is skipped. With no ids, every story is drilled.
     */
    public MadeCardsResponse drillStories(UUID ownerId, List<UUID> storyIds) {
        return tx.execute(status -> {
            var now = Instant.now(clock);
            var picked = stories.all(ownerId).stream()
                    .filter(story -> storyIds == null || storyIds.isEmpty() || storyIds.contains(story.getId()))
                    .toList();
            if (picked.isEmpty()) {
                throw new StudyException("Write a story first; each one becomes a card.");
            }
            var deck = decks.findByOwnerIdAndKind(ownerId, Deck.Kind.STORIES)
                    .orElseGet(() -> decks.saveAndFlush(Deck.stories(ownerId, now)));
            var drilled = cards.findAllByDeckIdOrderByCreatedAtAsc(deck.getId()).stream()
                    .map(Card::getStoryId)
                    .collect(Collectors.toSet());
            int added = 0;
            // Oldest first, so the deck reads in the order the stories were written.
            for (var story : picked.reversed()) {
                if (drilled.contains(story.getId())) continue;
                cards.save(Card.create(ownerId, deck.getId(), StoryCard.question(story), StoryCard.answer(story),
                        Card.Source.STORY, story.getId(), Card.Status.ACTIVE, now));
                added++;
            }
            deck.touch(now);
            cards.flush();
            return new MadeCardsResponse(study.deck(ownerId, deck.getId()).orElseThrow(), added, picked.size() - added);
        });
    }

    /** "What is OAuth?" and "what is  oauth" are the same question. */
    static String normalize(String question) {
        return question.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
