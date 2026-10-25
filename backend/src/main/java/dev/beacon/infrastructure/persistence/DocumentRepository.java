package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.documents.Document;
import dev.beacon.domain.documents.DocumentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every finder takes the owner, so one user's documents can't leak into another's queries. */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
            select new dev.beacon.domain.documents.DocumentInfo(
                d.id, d.applicationId, d.kind, d.filename, d.contentType, d.sizeBytes, d.createdAt)
            from Document d
            where d.ownerId = :ownerId and d.applicationId = :applicationId
            order by d.createdAt desc
            """)
    List<DocumentInfo> findInfoByApplication(UUID ownerId, UUID applicationId);

    @Query("""
            select new dev.beacon.domain.documents.DocumentInfo(
                d.id, d.applicationId, d.kind, d.filename, d.contentType, d.sizeBytes, d.createdAt)
            from Document d
            where d.ownerId = :ownerId and d.applicationId is null
            order by d.createdAt desc
            """)
    List<DocumentInfo> findLibraryInfo(UUID ownerId);

    Optional<Document> findByIdAndApplicationIdAndOwnerId(UUID id, UUID applicationId, UUID ownerId);

    Optional<Document> findByIdAndApplicationIdIsNullAndOwnerId(UUID id, UUID ownerId);
}
