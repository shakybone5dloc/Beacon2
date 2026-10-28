package dev.beacon.api.companies;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.companies.CompanyService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.companies.CompanyResponse;
import dev.beacon.contracts.companies.UpdateCompanyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Companies are created by naming them on an application; here they are listed, looked at and edited. */
@Tag(name = "Companies")
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @Operation(operationId = "listCompanies")
    @GetMapping
    public List<CompanyResponse> list(
            @AuthenticationPrincipal SignedInUser user,
            @Parameter(description = "Only companies whose name contains this (up to ten), for type-ahead")
            @RequestParam(required = false) String q
    ) {
        return service.list(user.id(), q);
    }

    @Operation(operationId = "getCompany")
    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> get(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(service.get(user.id(), id));
    }

    @Operation(operationId = "listCompanyApplications")
    @GetMapping("/{id}/applications")
    public ResponseEntity<List<ApplicationResponse>> applications(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(service.applications(user.id(), id));
    }

    @Operation(operationId = "updateCompany")
    @PutMapping("/{id}")
    public ResponseEntity<CompanyResponse> update(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request
    ) {
        try {
            return ResponseEntity.of(service.update(user.id(), id, request));
        } catch (CompanyService.NameTakenException ex) {
            throw new RequestValidationException("name", ex.getMessage());
        }
    }
}
