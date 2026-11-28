package dev.beacon.domain.applications;

import dev.beacon.domain.DomainException;
import dev.beacon.domain.companies.Company;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobApplicationRevertTests {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-10-23T12:00:00Z");
    private static final Instant T1 = T0.plusSeconds(3600);
    private static final Instant T2 = T0.plusSeconds(7200);
    private static final Instant T3 = T0.plusSeconds(10800);

    private static JobApplication newApp() {
        return JobApplication.create(OWNER, Company.create(OWNER, "Acme", T0), "Engineer", null, null, T0);
    }

    /** Stands in for the database assigning ids, in history order. */
    private static void assignIds(JobApplication app) {
        long next = 1;
        for (var c : app.getStatusHistory()) {
            if (c.getId() == null) ReflectionTestUtils.setField(c, "id", next);
            next++;
        }
    }

    private static StatusChange latest(JobApplication app) {
        return app.getStatusHistory().getLast();
    }

    @Test
    void revertsLatestUserMoveOutOfTerminalStatus() {
        var app = newApp();
        app.changeStatus(ApplicationStatus.APPLIED, T1);
        app.changeStatus(ApplicationStatus.REJECTED, T2);
        assignIds(app);

        app.revert(latest(app), T3);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(app.getStatusChangedAt()).isEqualTo(T3);
        assertThat(app.getUpdatedAt()).isEqualTo(T3);
        assertThat(app.getStatusHistory()).hasSize(4);
        var revert = latest(app);
        assertThat(revert.getFromStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(revert.getToStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(revert.getSource()).isEqualTo(StatusChangeSource.USER);
        assertThat(revert.getRevertsChangeId()).isEqualTo(3L);
    }

    @Test
    void revertsLatestEmailMove() {
        var app = newApp();
        app.changeStatus(ApplicationStatus.APPLIED, T1);
        app.changeStatus(ApplicationStatus.INTERVIEWING, T2, StatusChangeSource.EMAIL, "<m1@x>");
        assignIds(app);

        app.revert(latest(app), T3);

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(latest(app).getSource()).isEqualTo(StatusChangeSource.USER);
        assertThat(latest(app).getSourceEmailId()).isNull();
    }

    @Test
    void revertingTheRevertRedoesTheMove() {
        var app = newApp();
        app.changeStatus(ApplicationStatus.APPLIED, T1);
        app.changeStatus(ApplicationStatus.REJECTED, T2);
        assignIds(app);
        app.revert(latest(app), T3);
        assignIds(app);

        app.revert(latest(app), T3.plusSeconds(60));

        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(app.getStatusHistory()).hasSize(5);
        assertThat(latest(app).getRevertsChangeId()).isEqualTo(4L);
    }

    @Test
    void olderChangeCannotBeReverted() {
        var app = newApp();
        app.changeStatus(ApplicationStatus.APPLIED, T1);
        app.changeStatus(ApplicationStatus.REJECTED, T2);
        assignIds(app);
        var older = app.getStatusHistory().get(1);

        assertThatThrownBy(() -> app.revert(older, T3)).isInstanceOf(DomainException.class);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(app.getStatusHistory()).hasSize(3);
    }

    @Test
    void firstEntryWithoutFromStatusCannotBeReverted() {
        var app = newApp();
        assignIds(app);

        assertThatThrownBy(() -> app.revert(latest(app), T1))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("first");
        assertThat(app.getStatusHistory()).hasSize(1);
    }

    @Test
    void changeFromAnotherApplicationIsRejected() {
        var app = newApp();
        var other = newApp();
        other.changeStatus(ApplicationStatus.APPLIED, T1);
        assignIds(other);

        assertThatThrownBy(() -> app.revert(latest(other), T2)).isInstanceOf(DomainException.class);
    }
}
