package dev.beacon.application.documents;

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
    private final Clock clock;

    public DocumentService(DocumentRepository documents, JobApplicationRepository applications, Clock clock) {
        this.documents = documents;
        this.applications = applications;
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

    @Transactional
    public DocumentResponse addToLibrary(UUID ownerId, DocumentKind kind, String filename, String contentType, byte[] content) {
        return save(ownerId, null, kind, filename, contentType, content);
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
        found.ifPresent(documents::delete);
        return found.isPresent();
    }

    private DocumentResponse save(UUID ownerId, UUID applicationId, DocumentKind kind, String filename,
                                  String contentType, byte[] content) {
        var document = Document.create(ownerId, applicationId, kind, filename, contentType, content, Instant.now(clock));
        documents.save(document);
        return toResponse(document.info());
    }

    public static DocumentResponse toResponse(DocumentInfo d) {
        return new DocumentResponse(d.id(), d.applicationId(), d.kind().name(), d.filename(), d.contentType(),
                d.sizeBytes(), d.createdAt());
    }
}
