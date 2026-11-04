package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.briefs.BriefSection;
import dev.beacon.domain.briefs.SectionKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reached only through a brief already checked to belong to the user. */
public interface BriefSectionRepository extends JpaRepository<BriefSection, UUID> {

    List<BriefSection> findAllByBriefId(UUID briefId);

    /** A null interview id matches the sections that aren't about one round. */
    @Query("""
            select s from BriefSection s
            where s.briefId = :briefId and s.kind = :kind
              and (s.interviewId = :interviewId or (:interviewId is null and s.interviewId is null))
            """)
    Optional<BriefSection> findOne(UUID briefId, SectionKind kind, UUID interviewId);
}
