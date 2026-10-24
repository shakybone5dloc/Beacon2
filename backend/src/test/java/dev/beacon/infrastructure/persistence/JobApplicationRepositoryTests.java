package dev.beacon.infrastructure.persistence;

import dev.beacon.TestcontainersConfiguration;
import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.JobApplication;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JobApplicationRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.00Z");

    @Autowired JobApplicationRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void savesAndReloadsAnApplication() {
        var app = JobApplication.create("  Acme  ", "Senior Engineer", null, "   ", NOW);

        repository.saveAndFlush(app);
        entityManager.clear();

        var loaded = repository.findById(app.getId()).orElseThrow();

        assertThat(loaded.getCompany()).isEqualTo("Acme");
        assertThat(loaded.getNotes()).isNull();
        assertThat(loaded.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void mutlipleApplicationsReturnsNewestFirst() {
        var app1 = JobApplication.create("company A", "engineer", null, null, NOW);
        var app2 = JobApplication.create("company B", "engineer", null, null, NOW.plusSeconds(60));
        var app3 = JobApplication.create("company C", "engineer", null, null, NOW.minusSeconds(60));

        repository.saveAndFlush(app1);
        repository.saveAndFlush(app2);
        repository.saveAndFlush(app3);
        entityManager.clear();
        var loaded = repository.findAllByOrderByCreatedAtDesc();
        assertThat(loaded).hasSize(3);
        assertThat(loaded.getFirst().getCompany()).isEqualTo("company B");
    }

    @Test
    void persistsStatusHistoryInOrder() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);
        app.changeStatus(ApplicationStatus.APPLIED, NOW.plusSeconds(60));
        app.changeStatus(ApplicationStatus.INTERVIEWING, NOW.plusSeconds(120));

        repository.saveAndFlush(app);
        entityManager.clear();

        var loaded = repository.findById(app.getId()).orElseThrow();
        assertThat(loaded.getStatusChangedAt()).isEqualTo(NOW.plusSeconds(120));
        assertThat(loaded.getStatusHistory())
                .extracting(change -> change.getToStatus())
                .containsExactly(ApplicationStatus.SAVED, ApplicationStatus.APPLIED, ApplicationStatus.INTERVIEWING);
    }

    @Test
    void deletingAnApplicationDeletesItsHistory() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);
        app.changeStatus(ApplicationStatus.APPLIED, NOW.plusSeconds(60));
        repository.saveAndFlush(app);
        entityManager.clear();

        repository.deleteById(app.getId());
        repository.flush();

        var remaining = entityManager
                .createQuery("select count(c) from StatusChange c", Long.class)
                .getSingleResult();
        assertThat(remaining).isZero();
    }
}
