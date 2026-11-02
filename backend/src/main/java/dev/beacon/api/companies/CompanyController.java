package dev.beacon.api.companies;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.companies.CareersBoardService;
import dev.beacon.application.companies.CompanyService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.companies.CompanyResponse;
import dev.beacon.contracts.companies.CreateCompanyRequest;
import dev.beacon.contracts.companies.UpdateCompanyRequest;
import dev.beacon.contracts.companies.WatchCompanyRequest;
import dev.beacon.contracts.postings.JobPostingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Companies are created by naming them on an application or person, or added here by hand; here they are listed, looked at and edited. */
@Tag(name = "Companies")
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;
    private final CareersBoardService careersBoards;

    public CompanyController(CompanyService service, CareersBoardService careersBoards) {
        this.service = service;
        this.careersBoards = careersBoards;
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

    /** Adds a company by hand; a name the user already has returns that company (200) instead of a duplicate (201). */
    @Operation(operationId = "createCompany")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping
    public ResponseEntity<CompanyResponse> create(@AuthenticationPrincipal SignedInUser user,
                                                  @Valid @RequestBody CreateCompanyRequest request) {
        var created = service.create(user.id(), request);
        if (!created.isNew()) return ResponseEntity.ok(created.company());
        return ResponseEntity.created(URI.create("/api/companies/" + created.company().id())).body(created.company());
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

    /** Puts the company on the watchlist, from a link to its careers page. */
    @Operation(operationId = "watchCompany")
    @PutMapping("/{id}/careers-board")
    public ResponseEntity<CompanyResponse> watch(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                 @Valid @RequestBody WatchCompanyRequest request) {
        try {
            return ResponseEntity.of(careersBoards.watch(user.id(), id, request.url()));
        } catch (CareersBoardService.UnknownBoardException ex) {
            throw new RequestValidationException("url", ex.getMessage());
        }
    }

    @Operation(operationId = "stopWatchingCompany")
    @DeleteMapping("/{id}/careers-board")
    public ResponseEntity<CompanyResponse> stopWatching(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(careersBoards.stopWatching(user.id(), id));
    }

    /** What's open on the company's careers board; an empty list when it isn't watched. */
    @Operation(operationId = "listCompanyOpenings")
    @GetMapping("/{id}/openings")
    public ResponseEntity<List<JobPostingResponse>> openings(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        try {
            return ResponseEntity.of(careersBoards.openings(user.id(), id));
        } catch (CareersBoardService.BoardUnavailableException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }
}
