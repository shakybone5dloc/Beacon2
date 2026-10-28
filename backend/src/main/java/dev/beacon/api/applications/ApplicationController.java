package dev.beacon.api.applications;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.applications.ApplicationService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.ChangeStatusRequest;
import dev.beacon.contracts.applications.CreateApplicationRequest;
import dev.beacon.contracts.applications.FollowUpRequest;
import dev.beacon.contracts.applications.PostingFields;
import dev.beacon.contracts.applications.StatusChangeResponse;
import dev.beacon.contracts.applications.UpdateApplicationRequest;
import dev.beacon.domain.applications.ApplicationStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Applications")
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @Operation(operationId = "createApplication")
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@AuthenticationPrincipal SignedInUser user, @Valid @RequestBody CreateApplicationRequest request) {
        requireOrderedSalary(request);
        var response = service.create(user.id(), request);
        return ResponseEntity
                .created(URI.create("/api/applications/" + response.id()))
                .body(response);
    }

    @Operation(operationId = "getApplication")
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getById(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(service.getById(user.id(), id));
    }

    @Operation(operationId = "listApplications")
    @GetMapping
    public List<ApplicationResponse> getAll(@AuthenticationPrincipal SignedInUser user) {
        return service.list(user.id());
    }

    @Operation(operationId = "updateApplication")
    @PutMapping("/{id}")
    public ResponseEntity<ApplicationResponse> update(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationRequest request
    ) {
        requireOrderedSalary(request);
        return ResponseEntity.of(service.update(user.id(), id, request));
    }

    @Operation(operationId = "deleteApplication")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return service.delete(user.id(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(operationId = "scheduleFollowUp")
    @PutMapping("/{id}/follow-up")
    public ResponseEntity<ApplicationResponse> scheduleFollowUp(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID id,
            @Valid @RequestBody FollowUpRequest request
    ) {
        return ResponseEntity.of(service.scheduleFollowUp(user.id(), id, request));
    }

    @Operation(operationId = "getApplicationHistory")
    @GetMapping("/{id}/history")
    public ResponseEntity<List<StatusChangeResponse>> history(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(service.history(user.id(), id));
    }

    @Operation(operationId = "changeApplicationStatus")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> changeStatus(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        var status = parseStatus(request.status());
        return ResponseEntity.of(service.changeStatus(user.id(), id, status, request.version()));
    }

    private static void requireOrderedSalary(PostingFields fields) {
        if (fields.salaryMin() != null && fields.salaryMax() != null && fields.salaryMin() > fields.salaryMax()) {
            throw new RequestValidationException("salaryMax", "must be at least the minimum salary");
        }
    }

    private static ApplicationStatus parseStatus(String value) {
        try {
            return ApplicationStatus.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RequestValidationException("status", "'" + value + "' is not a valid status");
        }
    }
}
