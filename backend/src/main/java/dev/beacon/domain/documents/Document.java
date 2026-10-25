package dev.beacon.domain.documents;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A file kept with an application, or in the library when applicationId is
 * null. The bytes live in the row; listings use {@link DocumentInfo} so they
 * never load them.
 */
@Entity
@Table(name = "documents")
public class Document {

    public static final int MAX_BYTES = 10 * 1024 * 1024;

    @Id
    private UUID id;

    /** Plain column rather than a relation: documents never need the application loaded. */
    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    /** The user this document belongs to. Rows from before accounts existed have none. */
    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentKind kind;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private byte[] content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Document() { }

    public static Document create(UUID ownerId, UUID applicationId, DocumentKind kind, String filename,
                                  String contentType, byte[] content, Instant now) {
        if (ownerId == null) throw new IllegalArgumentException("A document needs an owner");
        if (content == null || content.length == 0) throw new IllegalArgumentException("Document is empty");
        if (content.length > MAX_BYTES) throw new IllegalArgumentException("Document is over the size limit");

        var document = new Document();
        document.id = UUID.randomUUID();
        document.applicationId = applicationId;
        document.ownerId = ownerId;
        document.kind = kind;
        document.filename = filename;
        document.contentType = contentType;
        document.sizeBytes = content.length;
        document.content = content;
        document.createdAt = now;
        return document;
    }

    public DocumentInfo info() {
        return new DocumentInfo(id, applicationId, kind, filename, contentType, sizeBytes, createdAt);
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public DocumentKind getKind() { return kind; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content; }
}
