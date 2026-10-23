package dev.beacon.domain.applications;

import dev.beacon.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class JobApplicationStatusTests {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00.00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private static JobApplication newApp() {
        return JobApplication.create("Acme", "Engineer", null, null, NOW);
    }

    @Test
    void allowedMoveChangesStatusAndUpdatedAt() {
        var app = newApp();

        app.changeStatus(ApplicationStatus.APPLIED, LATER);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(app.getUpdatedAt()).isEqualTo(LATER);
        assertThat(app.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void illegalMoveThrowsDomainException() {
        var app = newApp();

        assertThatThrownBy(() -> app.changeStatus(ApplicationStatus.OFFER, LATER))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("SAVED")
                .hasMessageContaining("OFFER");
    }

    @Test
    void statusChangeToSameStatusThrowsDomainException() {
        var app = newApp();

        assertThatThrownBy(() -> app.changeStatus(ApplicationStatus.SAVED, LATER))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("SAVED");
    }

    @Test
    void allowedFromRejectedAndWithdrawnAreEmpty() {

        var rejected = ApplicationStatusTransitions.allowedFrom(ApplicationStatus.REJECTED);
        var withdrawn =  ApplicationStatusTransitions.allowedFrom(ApplicationStatus.WITHDRAWN);
        assertThat(rejected).isEmpty();
        assertThat(withdrawn).isEmpty();
    }
}
