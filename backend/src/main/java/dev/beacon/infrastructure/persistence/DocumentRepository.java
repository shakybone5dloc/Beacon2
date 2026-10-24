package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.documents.Document;
import dev.beacon.domain.documents.DocumentInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
            select new dev.beacon.domain.documents.DocumentInfo(
                d.id, d.applicationId, d.kind, d.filename, d.contentType, d.sizeBytes, d.createdAt)
            from Document d
            where d.applicationId = :applicationId
            order by d.createdAt desc
            """)
    List<DocumentInfo> findInfoByApplicationId(UUID applicationId);

    @Query("""
            select new dev.beacon.domain.documents.DocumentInfo(
                d.id, d.applicationId, d.kind, d.filename, d.contentType, d.sizeBytes, d.createdAt)
            from Document d
            where d.applicationId is null
            order by d.createdAt desc
            """)
    List<DocumentInfo> findLibraryInfo();

    Optional<Document> findByIdAndApplicationId(UUID id, UUID applicationId);

    Optional<Document> findByIdAndApplicationIdIsNull(UUID id);
}
