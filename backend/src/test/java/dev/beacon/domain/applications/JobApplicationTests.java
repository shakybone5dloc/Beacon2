package dev.beacon.domain.applications;

import dev.beacon.domain.companies.Company;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JobApplicationTests {

    private static final UUID OWNER = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.00Z");

    private static Company company(String name) {
        return Company.create(OWNER, name, NOW);
    }

    @Test
    void createStartsInSavedStatus() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", null, null, NOW);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(app.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void blankRoleThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JobApplication.create(OWNER, company("Acme"), "   ", null, null, NOW));
    }

    @Test
    void theEmployerMayBeUndisclosed() {
        var app = JobApplication.create(OWNER, null, "Engineer", null, null, NOW);

        assertThat(app.getCompany()).isNull();
        assertThat(app.getCompanyName()).isNull();
        assertThat(app.getEmployerLabel()).isEqualTo("Undisclosed client");
    }

    @Test
    void anotherUsersCompanyIsRejected() {
        var theirs = Company.create(UUID.randomUUID(), "Acme", NOW);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> JobApplication.create(OWNER, theirs, "Engineer", null, null, NOW));
    }

    @Test
    void trimOrNullReturnsNullOrTrimmedString() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", "", null, NOW);
        var app2 = JobApplication.create(OWNER, company("Acme"), "Engineer", "  url  ", null, NOW);
        assertThat(app.getJobUrl()).isNull();
        assertThat(app2.getJobUrl()).isEqualTo("url");
    }

    @Test
    void updateDetailsTrimsValuesAndBumpsUpdatedAt() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", "https://acme.dev/job", "note", NOW);
        var later = NOW.plusSeconds(60);

        app.updateDetails(company("  Globex  "), "Staff Engineer", "  ", "  new notes  ", later);

        assertThat(app.getCompanyName()).isEqualTo("Globex");
        assertThat(app.getRole()).isEqualTo("Staff Engineer");
        assertThat(app.getJobUrl()).isNull();
        assertThat(app.getNotes()).isEqualTo("new notes");
        assertThat(app.getUpdatedAt()).isEqualTo(later);
        assertThat(app.getCreatedAt()).isEqualTo(NOW);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);
    }

    @Test
    void updateDetailsWithBlankRoleThrowsIllegalArgumentException() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", null, null, NOW);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> app.updateDetails(company("Acme"), " ", null, null, NOW));
    }

    @Test
    void scheduleFollowUpSetsAndClearsTheDate() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", null, null, NOW);
        var later = NOW.plusSeconds(60);

        app.scheduleFollowUp(LocalDate.parse("2026-11-07"), later);
        assertThat(app.getFollowUpOn()).isEqualTo(LocalDate.parse("2026-11-07"));
        assertThat(app.getUpdatedAt()).isEqualTo(later);

        app.scheduleFollowUp(null, later);
        assertThat(app.getFollowUpOn()).isNull();
    }

    @Test
    void followUpMayBeInThePast() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", null, null, NOW);

        app.scheduleFollowUp(LocalDate.parse("2020-01-01"), NOW);

        assertThat(app.getFollowUpOn()).isEqualTo(LocalDate.parse("2020-01-01"));
    }

    @Test
    void datesDoNotAffectStatus() {
        var app = JobApplication.create(OWNER, company("Acme"), "Engineer", null, null, NOW);

        app.setApplyBy(LocalDate.parse("2026-11-15"), NOW);
        app.scheduleFollowUp(LocalDate.parse("2026-11-01"), NOW);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(app.getStatusChangedAt()).isEqualTo(NOW);
    }
}
