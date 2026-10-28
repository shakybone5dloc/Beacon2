package dev.beacon.application.applications;

import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.PostingFields;
import dev.beacon.contracts.applications.StatusChangeResponse;
import dev.beacon.domain.applications.ApplicationStatusTransitions;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.applications.PostingDetails;
import dev.beacon.domain.applications.StatusChange;

public final class ApplicationMappings {
    private ApplicationMappings() {}

    /** Everything about one application, including its description and applied date. */
    public static ApplicationResponse toResponse(JobApplication a) {
        return toResponse(a, true, null);
    }

    /** @param viaAgency see {@link ApplicationResponse#viaAgency()} */
    public static ApplicationResponse toResponse(JobApplication a, String viaAgency) {
        return toResponse(a, true, viaAgency);
    }

    /** For lists: skips the description and the history lookup behind appliedAt. */
    public static ApplicationResponse toSummary(JobApplication a) {
        return toResponse(a, false, null);
    }

    public static ApplicationResponse toSummary(JobApplication a, String viaAgency) {
        return toResponse(a, false, viaAgency);
    }

    private static ApplicationResponse toResponse(JobApplication a, boolean full, String viaAgency) {
        var allowedTransitions = ApplicationStatusTransitions.allowedFrom(a.getStatus()).stream().map(Enum::name).toList();
        var posting = a.getPostingDetails();
        return new ApplicationResponse(
                a.getId(),
                a.getCompany() == null ? null : a.getCompany().getId(),
                a.getCompanyName(),
                a.getRole(),
                a.getEmploymentType(),
                viaAgency,
                a.getJobUrl(),
                a.getNotes(),
                a.getApplyBy(),
                a.getFollowUpOn(),
                posting.location(),
                posting.remote(),
                posting.salaryMin(),
                posting.salaryMax(),
                posting.salaryCurrency(),
                posting.salaryPeriod(),
                posting.salaryText(),
                posting.source(),
                posting.postedOn(),
                full ? a.getJobDescription() : null,
                full ? a.getAppliedAt() : null,
                a.getStatus().name(),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getStatusChangedAt(),
                a.getVersion(),
                allowedTransitions
        );
    }

    public static PostingDetails toPostingDetails(PostingFields f) {
        return new PostingDetails(f.location(), f.remote(), f.salaryMin(), f.salaryMax(), f.salaryCurrency(),
                f.salaryPeriod(), f.salaryText(), f.source(), f.postedOn());
    }

    public static StatusChangeResponse toResponse(StatusChange c) {
        return new StatusChangeResponse(
                c.getFromStatus() == null ? null : c.getFromStatus().name(),
                c.getToStatus().name(),
                c.getChangedAt()
        );
    }
}
