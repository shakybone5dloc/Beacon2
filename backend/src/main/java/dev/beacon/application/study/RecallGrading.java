package dev.beacon.application.study;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.study.AnswerCheckResponse;
import dev.beacon.domain.study.Card;
import dev.beacon.infrastructure.persistence.CardRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recall practice (A8): the AI reads a typed or spoken answer against the card and suggests a
 * grade with a short note. It only suggests; the scheduler uses the grade the user confirms.
 */
@Service
public class RecallGrading {

    /** Tried in this order: a user with both keys gets OpenAI, as everywhere else. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private final CardRepository cards;
    private final List<AnswerGrader> graders;
    private final UserCredentials credentials;
    private final AiUsageService usage;
    private final TransactionTemplate tx;

    public RecallGrading(CardRepository cards, List<AnswerGrader> graders, UserCredentials credentials,
                         AiUsageService usage, PlatformTransactionManager transactions) {
        this.cards = cards;
        this.graders = graders;
        this.credentials = credentials;
        this.usage = usage;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Whether answers can be checked: the user has an OpenAI or Anthropic key. */
    public boolean available(UUID ownerId) {
        return provider(ownerId).isPresent();
    }

    /** Empty when the card doesn't exist. Saves nothing: the grade is saved when the user picks one. */
    public Optional<AnswerCheckResponse> check(UUID ownerId, UUID cardId, String answer) {
        var card = tx.execute(status -> cards.findByIdAndOwnerId(cardId, ownerId)
                .map(c -> new AnswerGrader.Input(c.getQuestion(), c.getAnswer(), answer.strip())));
        if (card.isEmpty()) return Optional.empty();

        // The AI call is made outside any transaction, so it holds no database connection.
        var provider = provider(ownerId).orElseThrow(() ->
                new StudyException("Add an OpenAI or Anthropic API key under Settings to have answers checked."));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new StudyException(ex.getMessage());
        }
        var grader = graders.stream().filter(g -> g.provider().equals(provider)).findFirst().orElseThrow();
        var verdict = grader.grade(card.get(), apiKey, call -> usage.record(ownerId, AiFeature.RECALL_GRADING, call));
        return Optional.of(new AnswerCheckResponse(verdict.grade(), verdict.feedback(), verdict.tighter()));
    }

    private Optional<String> provider(UUID ownerId) {
        var saved = credentials.savedProviders(ownerId);
        return PREFERENCE.stream()
                .filter(saved::contains)
                .filter(id -> graders.stream().anyMatch(g -> g.provider().equals(id)))
                .findFirst();
    }
}
