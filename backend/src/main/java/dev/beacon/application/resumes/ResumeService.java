package dev.beacon.application.resumes;

import dev.beacon.contracts.resumes.ResumeResponse;
import dev.beacon.contracts.resumes.UpdateResumeRequest;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.documents.DocumentInfo;
import dev.beacon.domain.resumes.ResumeProfile;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.DocumentRepository;
import dev.beacon.infrastructure.persistence.ResumeProfileRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resume profiles: one per library resume, made when it's uploaded. Each user has at
 * most one default, and has one whenever they have any resume. Every method takes
 * the owner; another user's resumes behave as if they don't exist.
 */
@Service
public class ResumeService {

    private final ResumeProfileRepository profiles;
    private final DocumentRepository documents;
    private final Clock clock;

    public ResumeService(ResumeProfileRepository profiles, DocumentRepository documents, Clock clock) {
        this.profiles = profiles;
        this.documents = documents;
        this.clock = clock;
    }

    /** The default first, then newest first. Reads the text of any resume not read yet. */
    @Transactional
    public List<ResumeResponse> list(UUID ownerId) {
        var found = profiles.findAllByOwnerIdOrderByDefaultResumeDescCreatedAtDesc(ownerId);
        found.stream()
                .filter(p -> p.getTextStatus() == ResumeProfile.TextStatus.PENDING)
                .forEach(this::readText);
        var files = documents.findLibraryInfo(ownerId).stream()
                .collect(Collectors.toMap(DocumentInfo::id, Function.identity()));
        return found.stream()
                .filter(p -> files.containsKey(p.getDocumentId()))
                .map(p -> toResponse(p, files))
                .toList();
    }

    /** Called when a resume lands in the library; the user's first resume becomes the default. */
    @Transactional
    public void uploaded(Document resume) {
        var ownerId = resume.getOwnerId();
        var first = !profiles.existsByOwnerId(ownerId);
        profiles.save(ResumeProfile.create(resume.getId(), ownerId, resume.getFilename(), first,
                DocumentText.extract(resume.getContent(), resume.getContentType()), Instant.now(clock)));
    }

    /** Empty when the resume does not exist. */
    @Transactional
    public Optional<ResumeResponse> update(UUID ownerId, UUID documentId, UpdateResumeRequest request) {
        var found = profiles.findByDocumentIdAndOwnerId(documentId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var profile = found.get();
        if (profile.getVersion().longValue() != request.version()) {
            throw new OptimisticLockingFailureException(String.format(
                    "Resume %s has version %d, which does not match version %d", documentId, profile.getVersion(), request.version()));
        }
        profile.update(request.label(), request.targetTitles(), request.skills(), request.yearsExperience(), Instant.now(clock));
        profiles.flush();
        return Optional.of(respond(profile));
    }

    /** Makes this the user's default resume. Empty when it does not exist. */
    @Transactional
    public Optional<ResumeResponse> makeDefault(UUID ownerId, UUID documentId) {
        var found = profiles.findByDocumentIdAndOwnerId(documentId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var profile = found.get();
        var now = Instant.now(clock);
        profiles.findByOwnerIdAndDefaultResumeTrue(ownerId)
                .filter(current -> !current.getDocumentId().equals(documentId))
                .ifPresent(current -> {
                    current.clearDefault(now);
                    // Clear the old default first: at most one may be set at a time.
                    profiles.flush();
                });
        profile.makeDefault(now);
        profiles.flush();
        return Optional.of(respond(profile));
    }

    /** What Beacon read from the resume. Empty when it does not exist; an empty inner value when it has no text. */
    @Transactional
    public Optional<Optional<String>> text(UUID ownerId, UUID documentId) {
        return profiles.findByDocumentIdAndOwnerId(documentId, ownerId).map(profile -> {
            if (profile.getTextStatus() == ResumeProfile.TextStatus.PENDING) readText(profile);
            return profile.getText();
        });
    }

    /**
     * The library resume is being deleted: its profile goes too, and if it was the
     * default, the newest remaining resume takes over.
     */
    @Transactional
    public void deleting(UUID ownerId, UUID documentId) {
        profiles.findByDocumentIdAndOwnerId(documentId, ownerId).ifPresent(profile -> {
            var wasDefault = profile.isDefaultResume();
            profiles.delete(profile);
            profiles.flush();
            if (wasDefault) {
                profiles.findAllByOwnerIdOrderByDefaultResumeDescCreatedAtDesc(ownerId).stream()
                        .findFirst()
                        .ifPresent(next -> next.makeDefault(Instant.now(clock)));
            }
        });
    }

    private void readText(ResumeProfile profile) {
        documents.findById(profile.getDocumentId()).ifPresent(document ->
                profile.recordText(DocumentText.extract(document.getContent(), document.getContentType())));
    }

    private ResumeResponse respond(ResumeProfile profile) {
        var files = documents.findLibraryInfo(profile.getOwnerId()).stream()
                .collect(Collectors.toMap(DocumentInfo::id, Function.identity()));
        return toResponse(profile, files);
    }

    private static ResumeResponse toResponse(ResumeProfile p, Map<UUID, DocumentInfo> files) {
        var file = files.get(p.getDocumentId());
        return new ResumeResponse(p.getDocumentId(), p.getLabel(), p.isDefaultResume(), file.filename(),
                file.contentType(), file.sizeBytes(), p.getTextStatus(), p.getTargetTitles(), p.getSkills(),
                p.getYearsExperience(), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
