package dev.beacon.domain.applications;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class JobApplicationTests {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.00Z");

    @Test
    void createStartsInSavedStatus() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(app.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void blankCompanyThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> JobApplication.create("   ", "Engineer", null, null, NOW));
    }

    @Test
    void trimOrNullReturnsNullOrTrimmedString() {
        var app = JobApplication.create("Acme", "Engineer", "", null, NOW);
        var app2 = JobApplication.create("Acme", "Engineer", "  url  ", null, NOW);
        assertThat(app.getJobUrl()).isNull();
        assertThat(app2.getJobUrl()).isEqualTo("url");
    }

    @Test
    void updateDetailsTrimsValuesAndBumpsUpdatedAt() {
        var app = JobApplication.create("Acme", "Engineer", "https://acme.dev/job", "note", NOW);
        var later = NOW.plusSeconds(60);

        app.updateDetails("  Globex  ", "Staff Engineer", "  ", "  new notes  ", later);

        assertThat(app.getCompany()).isEqualTo("Globex");
        assertThat(app.getRole()).isEqualTo("Staff Engineer");
        assertThat(app.getJobUrl()).isNull();
        assertThat(app.getNotes()).isEqualTo("new notes");
        assertThat(app.getUpdatedAt()).isEqualTo(later);
        assertThat(app.getCreatedAt()).isEqualTo(NOW);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);
    }

    @Test
    void updateDetailsWithBlankRoleThrowsIllegalArgumentException() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> app.updateDetails("Acme", " ", null, null, NOW));
    }

    @Test
    void scheduleFollowUpSetsAndClearsTheDate() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);
        var later = NOW.plusSeconds(60);

        app.scheduleFollowUp(LocalDate.parse("2026-11-07"), later);
        assertThat(app.getFollowUpOn()).isEqualTo(LocalDate.parse("2026-11-07"));
        assertThat(app.getUpdatedAt()).isEqualTo(later);

        app.scheduleFollowUp(null, later);
        assertThat(app.getFollowUpOn()).isNull();
    }

    @Test
    void followUpMayBeInThePast() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);

        app.scheduleFollowUp(LocalDate.parse("2020-01-01"), NOW);

        assertThat(app.getFollowUpOn()).isEqualTo(LocalDate.parse("2020-01-01"));
    }

    @Test
    void datesDoNotAffectStatus() {
        var app = JobApplication.create("Acme", "Engineer", null, null, NOW);

        app.setApplyBy(LocalDate.parse("2026-11-15"), NOW);
        app.scheduleFollowUp(LocalDate.parse("2026-11-01"), NOW);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(app.getStatusChangedAt()).isEqualTo(NOW);
    }
}
