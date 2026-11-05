package dev.beacon.application.stories;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.resumes.ResumeService;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.resumes.ResumeResponse;
import dev.beacon.contracts.stories.StoryDraftResponse;
import dev.beacon.domain.stories.Story;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Drafts stories with the AI for the user to review: one from their notes, or a few from
 * their resume. Nothing is saved here; the user keeps the drafts they want.
 */
@Service
public class StoryDrafts {

    /** Tried in this order: a user with both keys gets OpenAI, as everywhere else. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private final DocumentService documents;
    private final ResumeService resumes;
    private final List<StoryWriter> writers;
    private final UserCredentials credentials;
    private final AiUsageService usage;
    private final Clock clock;

    public StoryDrafts(DocumentService documents, ResumeService resumes, List<StoryWriter> writers,
                       UserCredentials credentials, AiUsageService usage, Clock clock) {
        this.documents = documents;
        this.resumes = resumes;
        this.writers = writers;
        this.credentials = credentials;
        this.usage = usage;
        this.clock = clock;
    }

    public List<StoryDraftResponse> draft(UUID ownerId, String notes, UUID resumeDocumentId) {
        var hasNotes = notes != null && !notes.isBlank();
        var resume = resumeText(ownerId, resumeDocumentId);
        if (!hasNotes && resume.isEmpty()) {
            throw new StoryException("Add your notes on the story, or a resume Beacon can read, to draft from.", false);
        }

        var provider = PREFERENCE.stream()
                .filter(credentials.savedProviders(ownerId)::contains)
                .filter(id -> writers.stream().anyMatch(w -> w.provider().equals(id)))
                .findFirst()
                .orElseThrow(() -> new StoryException("Add an OpenAI or Anthropic API key under Settings to draft stories.", false));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new StoryException(ex.getMessage(), false);
        }

        var writer = writers.stream().filter(w -> w.provider().equals(provider)).findFirst().orElseThrow();
        var drafts = writer.draft(new StoryWriter.DraftInput(hasNotes ? notes.strip() : null, resume.orElse(null)),
                apiKey, call -> usage.record(ownerId, AiFeature.STORY_BANK, call));

        // Through the entity, so tags are cleaned and gaps found the same way as for saved stories.
        var now = Instant.now(clock);
        return drafts.stream()
                .map(d -> Story.create(ownerId, new Story.Fields(d.title(), d.situation(), d.task(), d.action(),
                        d.result(), d.metrics(), d.tags()), now))
                .map(s -> new StoryDraftResponse(s.getTitle(), s.getSituation(), s.getTask(), s.getAction(),
                        s.getResult(), s.getMetrics(), s.getTags(), s.gaps()))
                .toList();
    }

    /** The chosen resume, else the library's default; empty when there's none Beacon can read. */
    private Optional<String> resumeText(UUID ownerId, UUID resumeDocumentId) {
        var id = Optional.ofNullable(resumeDocumentId).or(() -> resumes.list(ownerId).stream()
                .filter(ResumeResponse::isDefault).map(ResumeResponse::documentId).findFirst());
        return id.flatMap(documentId -> documents.find(ownerId, null, documentId))
                .flatMap(resume -> resumes.text(ownerId, resume.getId()).flatMap(text -> text)
                        .or(() -> DocumentText.extract(resume.getContent(), resume.getContentType())));
    }
}
