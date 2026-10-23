package dev.beacon.api.applications;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.applications.ApplicationService;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.applications.ChangeStatusRequest;
import dev.beacon.contracts.applications.CreateApplicationRequest;
import dev.beacon.domain.applications.ApplicationStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        var response = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/applications/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApplicationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.of(service.getById(id));
    }

    @GetMapping
    public List<ApplicationResponse> getAll() {
        return service.list();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        var status = parseStatus(request.status());
        return ResponseEntity.of(service.changeStatus(id, status, request.version()));
    }

    private static ApplicationStatus parseStatus(String value) {
        try {
            return ApplicationStatus.valueOf(value.strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RequestValidationException("status", "'" + value + "' is not a valid status");
        }
    }
}
