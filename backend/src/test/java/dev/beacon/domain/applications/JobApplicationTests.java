package dev.beacon.domain.applications;

import org.junit.jupiter.api.Test;
import java.time.Instant;
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
}