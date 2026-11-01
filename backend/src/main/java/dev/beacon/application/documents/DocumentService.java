package dev.beacon.application.documents;

import dev.beacon.application.resumes.ResumeService;
import dev.beacon.contracts.documents.DocumentResponse;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.documents.DocumentInfo;
import dev.beacon.domain.documents.DocumentKind;
import dev.beacon.infrastructure.persistence.DocumentRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Documents attached to an application, or kept in the library when the
 * application id is null. Every method takes the owner; another user's
 * documents and applications behave as if they don't exist.
 */
@Service
public class DocumentService {

    private final DocumentRepository documents;
    private final JobApplicationRepository applications;
    private final ResumeService resumes;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, JobApplicationRepository applications, ResumeService resumes,
                           Clock clock) {
        this.documents = documents;
        this.applications = applications;
        this.resumes = resumes;
        this.clock = clock;
    }

    /** Empty when the application does not exist. */
    @Transactional(readOnly = true)
    public Optional<List<DocumentResponse>> listForApplication(UUID ownerId, UUID applicationId) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) return Optional.empty();
        return Optional.of(documents.findInfoByApplication(ownerId, applicationId).stream().map(DocumentService::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listLibrary(UUID ownerId) {
        return documents.findLibraryInfo(ownerId).stream().map(DocumentService::toResponse).toList();
    }

    /** Empty when the application does not exist. */
    @Transactional
    public Optional<DocumentResponse> addToApplication(UUID ownerId, UUID applicationId, DocumentKind kind,
                                                       String filename, String contentType, byte[] content) {
        if (!applications.existsByIdAndOwnerId(applicationId, ownerId)) return Optional.empty();
        return Optional.of(save(ownerId, applicationId, kind, filename, contentType, content));
    }

    /** A resume also gets a profile (label, default, its text read once). */
    @Transactional
    public DocumentResponse addToLibrary(UUID ownerId, DocumentKind kind, String filename, String contentType, byte[] content) {
        var document = create(ownerId, null, kind, filename, contentType, content);
        if (kind == DocumentKind.RESUME) resumes.uploaded(document);
        return toResponse(document.info());
    }

    /** A null application id looks in the library. */
    @Transactional(readOnly = true)
    public Optional<Document> find(UUID ownerId, UUID applicationId, UUID documentId) {
        return applicationId == null
                ? documents.findByIdAndApplicationIdIsNullAndOwnerId(documentId, ownerId)
                : documents.findByIdAndApplicationIdAndOwnerId(documentId, applicationId, ownerId);
    }

    /** A null application id deletes from the library. */
    @Transactional
    public boolean delete(UUID ownerId, UUID applicationId, UUID documentId) {
        var found = find(ownerId, applicationId, documentId);
        found.ifPresent(document -> {
            // A library resume's profile goes first, and another resume may become the default.
            if (applicationId == null) resumes.deleting(ownerId, document.getId());
            documents.delete(document);
        });
        return found.isPresent();
    }

    private DocumentResponse save(UUID ownerId, UUID applicationId, DocumentKind kind, String filename,
                                  String contentType, byte[] content) {
        return toResponse(create(ownerId, applicationId, kind, filename, contentType, content).info());
    }

    private Document create(UUID ownerId, UUID applicationId, DocumentKind kind, String filename,
                            String contentType, byte[] content) {
        var document = Document.create(ownerId, applicationId, kind, filename, contentType, content, Instant.now(clock));
        return documents.save(document);
    }

    public static DocumentResponse toResponse(DocumentInfo d) {
        return new DocumentResponse(d.id(), d.applicationId(), d.kind().name(), d.filename(), d.contentType(),
                d.sizeBytes(), d.createdAt());
    }
}
