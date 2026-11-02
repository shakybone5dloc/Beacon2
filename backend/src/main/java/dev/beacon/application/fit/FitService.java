package dev.beacon.application.fit;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.resumes.ResumeService;
import dev.beacon.contracts.fit.ApplicationFitResponse;
import dev.beacon.contracts.fit.FitResponse;
import dev.beacon.contracts.fit.ScorePostingFitRequest;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.fit.ApplicationFit;
import dev.beacon.domain.fit.FitScore;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.ApplicationFitRepository;
import dev.beacon.infrastructure.persistence.FitScoreRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Scores how well a resume fits a posting, for an application or a job search result.
 * A score is kept by a hash of everything it was made from, so checking the same
 * posting and resume again costs nothing; changing either gives a fresh score.
 */
@Service
public class FitService {

    /** Part of every hash: bump it when the prompt changes enough that old scores shouldn't be reused. */
    static final String PROMPT_VERSION = "fit-v1";

    /** Tried in this order: a user with both keys gets OpenAI, as with cover letters. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private final JobApplicationRepository applications;
    private final FitScoreRepository scores;
    private final ApplicationFitRepository links;
    private final DocumentService documents;
    private final ResumeService resumes;
    private final List<FitScorer> scorers;
    private final UserCredentials credentials;
    private final Clock clock;

    public FitService(JobApplicationRepository applications, FitScoreRepository scores, ApplicationFitRepository links,
                      DocumentService documents, ResumeService resumes, List<FitScorer> scorers,
                      UserCredentials credentials, Clock clock) {
        this.applications = applications;
        this.scores = scores;
        this.links = links;
        this.documents = documents;
        this.resumes = resumes;
        this.scorers = scorers;
        this.credentials = credentials;
        this.clock = clock;
    }

    /** True when the user has saved a key for any AI provider Beacon can score with. */
    public boolean isConfigured(UUID userId) {
        return chooseProvider(credentials.savedProviders(userId)).isPresent();
    }

    /** The application's latest fit. Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<ApplicationFitResponse> forApplication(UUID ownerId, UUID applicationId) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();

        var fit = links.findById(applicationId)
                .flatMap(link -> scores.findByIdAndOwnerId(link.getFitScoreId(), ownerId))
                .map(score -> {
                    // Current when the description and the resume's text are what it was scored from.
                    var resume = resume(ownerId, applicationId, score.getResumeDocumentId());
                    var current = application.getJobDescription() != null && resume.isPresent()
                            && resumeText(ownerId, resume.get())
                                    .map(text -> hash(score.getResumeDocumentId(), application.getCompanyName(),
                                            application.getRole(), application.getJobDescription(), text))
                                    .filter(score.getInputHash()::equals)
                                    .isPresent();
                    return toResponse(score, label(ownerId, resume), current);
                });
        return Optional.of(new ApplicationFitResponse(fit.orElse(null)));
    }

    /**
     * Scores the application's job description against a resume and makes it the
     * application's fit. A deadline the posting states fills an empty apply-by date.
     * Empty when the application does not exist.
     */
    @Transactional
    public Optional<FitResponse> scoreApplication(UUID ownerId, UUID applicationId, UUID resumeDocumentId) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();

        if (application.getJobDescription() == null) {
            throw new FitScoreException("Save the posting as a PDF or paste the job description into this application first.", false);
        }
        var resume = resume(ownerId, applicationId, resumeDocumentId)
                .orElseThrow(() -> new FitScoreException("That resume was not found.", false));

        var score = score(ownerId, resume, application.getCompanyName(), application.getRole(), application.getJobDescription());

        var now = Instant.now(clock);
        links.findById(applicationId).ifPresentOrElse(
                link -> link.relink(score.getId(), now),
                () -> links.save(ApplicationFit.create(applicationId, score.getId(), now)));
        if (application.getApplyBy() == null && score.getApplyBy() != null && !score.getApplyBy().isBefore(LocalDate.now(clock))) {
            application.setApplyBy(score.getApplyBy(), now);
        }
        return Optional.of(toResponse(score, label(ownerId, Optional.of(resume)), true));
    }

    /** Scores a job search result against a library resume; nothing is saved but the score. */
    @Transactional
    public FitResponse scorePosting(UUID ownerId, ScorePostingFitRequest request) {
        var resume = documents.find(ownerId, null, request.resumeDocumentId())
                .orElseThrow(() -> new FitScoreException("That resume was not found.", false));
        var company = request.company() == null || request.company().isBlank() ? null : request.company().strip();
        var score = score(ownerId, resume, company, request.title(), request.description());
        return toResponse(score, label(ownerId, Optional.of(resume)), true);
    }

    /** The kept score for these inputs, or a new one from the AI. */
    private FitScore score(UUID ownerId, Document resume, String company, String role, String description) {
        role = role.strip();
        description = description.strip();
        var resumeText = resumeText(ownerId, resume).orElseThrow(() -> new FitScoreException(
                "Beacon can read PDF, text and Markdown resumes. Upload your resume in one of those formats to check fit.", false));
        var inputHash = hash(resume.getId(), company, role, description, resumeText);
        var kept = scores.findByOwnerIdAndInputHash(ownerId, inputHash);
        if (kept.isPresent()) return kept.get();

        var provider = chooseProvider(credentials.savedProviders(ownerId)).orElseThrow(() -> new FitScoreException(
                "Add an OpenAI or Anthropic API key under Settings to check fit.", false));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new FitScoreException(ex.getMessage(), false);
        }

        var scorer = scorer(provider);
        var result = scorer.score(new FitScorer.FitInput(company, role, description, resumeText, LocalDate.now(clock)), apiKey);
        return scores.save(FitScore.create(ownerId, resume.getId(), inputHash, result.score(), result.matchedSkills(),
                result.gaps(), result.dealbreakers(), result.rationale(), result.applyBy(), provider, scorer.model(),
                Instant.now(clock)));
    }

    /** A resume attached to the application, or one in the library. */
    private Optional<Document> resume(UUID ownerId, UUID applicationId, UUID documentId) {
        return documents.find(ownerId, applicationId, documentId).or(() -> documents.find(ownerId, null, documentId));
    }

    /** A library resume's text was read once and kept; one attached to an application is read now. */
    private Optional<String> resumeText(UUID ownerId, Document resume) {
        return resumes.text(ownerId, resume.getId()).flatMap(text -> text)
                .or(() -> DocumentText.extract(resume.getContent(), resume.getContentType()));
    }

    private String label(UUID ownerId, Optional<Document> resume) {
        return resume.map(r -> resumes.label(ownerId, r.getId()).orElse(r.getFilename())).orElse("A deleted resume");
    }

    private Optional<String> chooseProvider(Set<String> saved) {
        return PREFERENCE.stream()
                .filter(saved::contains)
                .filter(id -> scorers.stream().anyMatch(s -> s.provider().equals(id)))
                .findFirst();
    }

    private FitScorer scorer(String provider) {
        return scorers.stream().filter(s -> s.provider().equals(provider)).findFirst().orElseThrow();
    }

    /**
     * SHA-256 over every input, each part separated so "ab"+"c" and "a"+"bc" differ. Stray
     * whitespace is ignored, so a search result saved to the board hashes the same.
     */
    static String hash(UUID resumeId, String company, String role, String description, String resumeText) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var part : new String[]{PROMPT_VERSION, resumeId.toString(), company == null ? "" : company.strip(),
                    role.strip(), description.strip(), resumeText}) {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is always available", ex);
        }
    }

    private static FitResponse toResponse(FitScore s, String resumeLabel, boolean current) {
        return new FitResponse(s.getId(), s.getScore(), s.getMatchedSkills(), s.getGaps(), s.getDealbreakers(),
                s.getRationale(), s.getApplyBy(), s.getResumeDocumentId(), resumeLabel, s.getCreatedAt(), current);
    }
}
