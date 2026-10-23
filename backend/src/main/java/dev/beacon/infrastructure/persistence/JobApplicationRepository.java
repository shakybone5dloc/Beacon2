package dev.beacon.infrastructure.persistence;

import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.applications.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    List<JobApplication> findAllByOrderByCreatedAtDesc();
    List<JobApplication> findByStatus(ApplicationStatus status);
    List<JobApplication> findByCompanyContainingIgnoreCase(String text);
    long countByStatus(ApplicationStatus status);
}