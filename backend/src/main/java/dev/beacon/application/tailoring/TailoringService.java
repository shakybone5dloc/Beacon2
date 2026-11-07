package dev.beacon.application.tailoring;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.resumes.ResumeService;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.resumes.ResumeResponse;
import dev.beacon.contracts.tailoring.ResumeSuggestionResponse;
import dev.beacon.contracts.tailoring.TailoringResponse;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.fit.FitScore;
import dev.beacon.domain.tailoring.ResumeSuggestion;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.ApplicationFitRepository;
import dev.beacon.infrastructure.persistence.FitScoreRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.persistence.ResumeSuggestionRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tailoring a resume to one application (A2): the AI suggests edits, the user marks each
 * done or dismisses it, and the resume file is never touched. Also records which resume
 * was actually sent, so Stats can compare resumes by what they led to.
 */
@Service
public class TailoringService {

    /** Tried in this order: a user with both keys gets OpenAI, as everywhere else. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private final JobApplicationRepository applications;
    private final ResumeSuggestionRepository suggestions;
    private final ApplicationFitRepository fitLinks;
    private final FitScoreRepository fitScores;
    private final DocumentService documents;
    private final ResumeService resumes;
    private final List<ResumeTailor> tailors;
    private final UserCredentials credentials;
    private final AiUsageService usage;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public TailoringService(JobApplicationRepository applications, ResumeSuggestionRepository suggestions,
                            ApplicationFitRepository fitLinks, FitScoreRepository fitScores,
                            DocumentService documents, ResumeService resumes, List<ResumeTailor> tailors,
                            UserCredentials credentials, AiUsageService usage, NamedParameterJdbcTemplate jdbc,
                            Clock clock) {
        this.applications = applications;
        this.suggestions = suggestions;
        this.fitLinks = fitLinks;
        this.fitScores = fitScores;
        this.documents = documents;
        this.resumes = resumes;
        this.tailors = tailors;
        this.credentials = credentials;
        this.usage = usage;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<TailoringResponse> get(UUID ownerId, UUID applicationId) {
        if (applications.findByIdAndOwnerId(applicationId, ownerId).isEmpty()) return Optional.empty();
        return Optional.of(response(ownerId, applicationId));
    }

    /**
     * Asks the AI for edits that tailor a resume to the application's posting. The open and
     * dismissed suggestions are replaced; the ones marked done are kept, first.
     * Empty when the application does not exist.
     */
    @Transactional
    public Optional<TailoringResponse> suggest(UUID ownerId, UUID applicationId, UUID resumeDocumentId) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();
        if (application.getJobDescription() == null) {
            throw new TailoringException("Save the posting as a PDF or paste the job description into this application first.", false);
        }

        var fit = latestFit(ownerId, applicationId);
        var resume = chooseResume(ownerId, applicationId, resumeDocumentId, fit)
                .orElseThrow(() -> new TailoringException(resumeDocumentId == null
                        ? "Upload a resume to your library, or to this application, to tailor it."
                        : "That resume was not found.", false));
        var resumeText = resumeText(ownerId, resume).orElseThrow(() -> new TailoringException(
                "Beacon can read PDF, text and Markdown resumes. Upload your resume in one of those formats to tailor it.", false));

        var provider = PREFERENCE.stream()
                .filter(credentials.savedProviders(ownerId)::contains)
                .filter(id -> tailors.stream().anyMatch(t -> t.provider().equals(id)))
                .findFirst()
                .orElseThrow(() -> new TailoringException("Add an OpenAI or Anthropic API key under Settings to get suggestions.", false));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new TailoringException(ex.getMessage(), false);
        }

        // The fit's findings only help when they were made from this resume.
        var findings = fit.filter(f -> f.getResumeDocumentId().equals(resume.getId()));
        var tailor = tailors.stream().filter(t -> t.provider().equals(provider)).findFirst().orElseThrow();
        var made = tailor.suggest(new ResumeTailor.TailorInput(application.getCompanyName(), application.getRole(),
                        application.getJobDescription(), resumeText,
                        findings.map(FitScore::getGaps).orElse(List.of()),
                        findings.map(FitScore::getMatchedSkills).orElse(List.of())),
                apiKey, call -> usage.record(ownerId, AiFeature.RESUME_TAILORING, call));

        var now = Instant.now(clock);
        var existing = suggestions.findAllByOwnerIdAndApplicationIdOrderByPositionAsc(ownerId, applicationId);
        var kept = new ArrayList<ResumeSuggestion>();
        for (var s : existing) {
            if (s.getState() == ResumeSuggestion.State.DONE) kept.add(s);
            else suggestions.delete(s);
        }
        suggestions.flush();
        for (int i = 0; i < kept.size(); i++) kept.get(i).moveTo(i);
        for (int i = 0; i < made.size(); i++) {
            var m = made.get(i);
            suggestions.save(ResumeSuggestion.create(ownerId, applicationId, resume.getId(), kept.size() + i,
                    m.kind(), m.section(), m.suggestion(), m.reason(), m.example(), tailor.model(), now));
        }
        suggestions.flush();
        return Optional.of(response(ownerId, applicationId));
    }

    /** Marks a suggestion done, dismissed or open again. Empty when it does not exist. */
    @Transactional
    public Optional<ResumeSuggestionResponse> mark(UUID ownerId, UUID applicationId, UUID suggestionId,
                                                   ResumeSuggestion.State state) {
        return suggestions.findByIdAndOwnerIdAndApplicationId(suggestionId, ownerId, applicationId).map(s -> {
            s.mark(state, Instant.now(clock));
            return toResponse(s);
        });
    }

    /**
     * Records the resume sent with the application: one in the library or attached to it.
     * Null clears it. Empty when the application does not exist.
     */
    @Transactional
    public Optional<TailoringResponse> setSent(UUID ownerId, UUID applicationId, UUID resumeDocumentId) {
        if (applications.findByIdAndOwnerId(applicationId, ownerId).isEmpty()) return Optional.empty();
        if (resumeDocumentId != null && findResume(ownerId, applicationId, resumeDocumentId).isEmpty()) {
            throw new TailoringException("That resume was not found.", false);
        }
        // Straight to the column: the application's own version stays, so an open edit form isn't made stale.
        jdbc.update("update job_applications set sent_resume_id = :resume where id = :id and owner_id = :owner",
                new MapSqlParameterSource()
                        .addValue("resume", resumeDocumentId)
                        .addValue("id", applicationId)
                        .addValue("owner", ownerId));
        return Optional.of(response(ownerId, applicationId));
    }

    private TailoringResponse response(UUID ownerId, UUID applicationId) {
        var list = suggestions.findAllByOwnerIdAndApplicationIdOrderByPositionAsc(ownerId, applicationId);
        // The resume of the newest suggestions, which a new request may have changed.
        var resumeId = list.stream()
                .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(ResumeSuggestion::getResumeDocumentId)
                .orElse(null);
        var label = resumeId == null ? null : findResume(ownerId, applicationId, resumeId)
                .map(d -> resumes.label(ownerId, d.getId()).orElse(d.getFilename()))
                .orElse("A deleted resume");
        var sent = jdbc.queryForList("select sent_resume_id from job_applications where id = :id",
                Map.of("id", applicationId), UUID.class).stream().filter(Objects::nonNull).findFirst().orElse(null);
        return new TailoringResponse(sent, resumeId, label, list.stream().map(TailoringService::toResponse).toList(),
                isConfigured(ownerId));
    }

    private boolean isConfigured(UUID ownerId) {
        Set<String> saved = credentials.savedProviders(ownerId);
        return PREFERENCE.stream().anyMatch(id -> saved.contains(id)
                && tailors.stream().anyMatch(t -> t.provider().equals(id)));
    }

    private Optional<FitScore> latestFit(UUID ownerId, UUID applicationId) {
        return fitLinks.findById(applicationId).flatMap(link -> fitScores.findByIdAndOwnerId(link.getFitScoreId(), ownerId));
    }

    /** The resume asked for, else the one the fit was checked with, else the library's default. */
    private Optional<Document> chooseResume(UUID ownerId, UUID applicationId, UUID requested, Optional<FitScore> fit) {
        if (requested != null) return findResume(ownerId, applicationId, requested);
        return fit.flatMap(f -> findResume(ownerId, applicationId, f.getResumeDocumentId()))
                .or(() -> resumes.list(ownerId).stream().filter(ResumeResponse::isDefault)
                        .findFirst().flatMap(r -> documents.find(ownerId, null, r.documentId())));
    }

    /** A resume attached to the application, or one in the library. */
    private Optional<Document> findResume(UUID ownerId, UUID applicationId, UUID documentId) {
        return documents.find(ownerId, applicationId, documentId).or(() -> documents.find(ownerId, null, documentId));
    }

    /** A library resume's text was read once and kept; one attached to an application is read now. */
    private Optional<String> resumeText(UUID ownerId, Document resume) {
        return resumes.text(ownerId, resume.getId()).flatMap(text -> text)
                .or(() -> DocumentText.extract(resume.getContent(), resume.getContentType()));
    }

    static ResumeSuggestionResponse toResponse(ResumeSuggestion s) {
        return new ResumeSuggestionResponse(s.getId(), s.getKind().name(), s.getSection(), s.getSuggestion(),
                s.getReason(), s.getExample(), s.getState().name(), s.getCreatedAt());
    }
}
