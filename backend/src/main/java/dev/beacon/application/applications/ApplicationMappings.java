package dev.beacon.application.applications;

import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.StatusChangeResponse;
import dev.beacon.domain.applications.ApplicationStatusTransitions;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.applications.StatusChange;

public final class ApplicationMappings {
    private ApplicationMappings() {}

    public static ApplicationResponse toResponse(JobApplication a) {
        var allowedTransitions = ApplicationStatusTransitions.allowedFrom(a.getStatus()).stream().map(Enum::name).toList();
        return new ApplicationResponse(
                a.getId(),
                a.getCompany(),
                a.getRole(),
                a.getJobUrl(),
                a.getNotes(),
                a.getStatus().name(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getStatusChangedAt(),
                a.getVersion(),
                allowedTransitions
        );
    }

    public static StatusChangeResponse toResponse(StatusChange c) {
        return new StatusChangeResponse(
                c.getFromStatus() == null ? null : c.getFromStatus().name(),
                c.getToStatus().name(),
                c.getChangedAt()
        );
    }
}
