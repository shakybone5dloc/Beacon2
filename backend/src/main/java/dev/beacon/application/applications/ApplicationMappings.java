package dev.beacon.application.applications;

import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.domain.applications.JobApplication;

public final class ApplicationMappings {
    private ApplicationMappings() {}

    public static ApplicationResponse toResponse(JobApplication a) {
        return new ApplicationResponse(
                a.getId(),
                a.getCompany(),
                a.getRole(),
                a.getJobUrl(),
                a.getNotes(),
                a.getStatus().name(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getVersion()
        );
    }
}
