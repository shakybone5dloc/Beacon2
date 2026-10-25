package dev.beacon.infrastructure.persistence;

import dev.beacon.TestcontainersConfiguration;
import dev.beacon.domain.applications.ApplicationStatus;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.users.UserAccount;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JobApplicationRepositoryTests {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.00Z");

    @Autowired JobApplicationRepository repository;
    @Autowired UserAccountRepository users;
    @Autowired EntityManager entityManager;

    private UUID owner;

    @BeforeEach
    void createOwner() {
        owner = newUser();
    }

    private UUID newUser() {
        var user = UserAccount.register("user-" + UUID.randomUUID() + "@example.com", "{noop}x", null, NOW);
        return users.save(user).getId();
    }

    @Test
    void savesAndReloadsAnApplication() {
        var app = JobApplication.create(owner, "  Acme  ", "Senior Engineer", null, "   ", NOW);

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
        var app1 = JobApplication.create(owner, "company A", "engineer", null, null, NOW);
        var app2 = JobApplication.create(owner, "company B", "engineer", null, null, NOW.plusSeconds(60));
        var app3 = JobApplication.create(owner, "company C", "engineer", null, null, NOW.minusSeconds(60));

        repository.saveAndFlush(app1);
        repository.saveAndFlush(app2);
        repository.saveAndFlush(app3);
        entityManager.clear();
        var loaded = repository.findAllByOwnerIdOrderByCreatedAtDesc(owner);
        assertThat(loaded).hasSize(3);
        assertThat(loaded.getFirst().getCompany()).isEqualTo("company B");
    }

    @Test
    void persistsStatusHistoryInOrder() {
        var app = JobApplication.create(owner, "Acme", "Engineer", null, null, NOW);
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
        var app = JobApplication.create(owner, "Acme", "Engineer", null, null, NOW);
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

    @Test
    void ownerFindersSkipOtherUsersAndUnownedRows() {
        var mine = JobApplication.create(owner, "Mine", "Engineer", null, null, NOW);
        var theirs = JobApplication.create(newUser(), "Theirs", "Engineer", null, null, NOW);
        repository.saveAndFlush(mine);
        repository.saveAndFlush(theirs);
        // A row from before accounts existed: no owner.
        entityManager.createNativeQuery("""
                insert into job_applications (id, company, role, status, version, created_at, updated_at, status_changed_at)
                values (:id, 'Legacy', 'Engineer', 'SAVED', 0, now(), now(), now())
                """).setParameter("id", UUID.randomUUID()).executeUpdate();
        entityManager.clear();

        assertThat(repository.findAllByOwnerIdOrderByCreatedAtDesc(owner))
                .extracting(JobApplication::getCompany).containsExactly("Mine");
        assertThat(repository.findByIdAndOwnerId(theirs.getId(), owner)).isEmpty();
        assertThat(repository.existsByIdAndOwnerId(theirs.getId(), owner)).isFalse();
        assertThat(repository.existsByIdAndOwnerId(mine.getId(), owner)).isTrue();
    }
}
