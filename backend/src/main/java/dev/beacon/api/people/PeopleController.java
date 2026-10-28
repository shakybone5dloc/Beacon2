package dev.beacon.api.people;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.companies.CompanyService;
import dev.beacon.application.people.PeopleService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.people.CreatePersonRequest;
import dev.beacon.contracts.people.PersonApplicationResponse;
import dev.beacon.contracts.people.PersonResponse;
import dev.beacon.contracts.people.UpdatePersonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Tag(name = "People")
@RestController
public class PeopleController {

    private final PeopleService service;

    public PeopleController(PeopleService service) {
        this.service = service;
    }

    @Operation(operationId = "listPeople")
    @GetMapping("/api/people")
    public List<PersonResponse> list(
            @AuthenticationPrincipal SignedInUser user,
            @Parameter(description = "Only people whose name contains this (up to ten), for type-ahead")
            @RequestParam(required = false) String q
    ) {
        return service.list(user.id(), q);
    }

    @Operation(operationId = "createPerson")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping("/api/people")
    public ResponseEntity<PersonResponse> create(@AuthenticationPrincipal SignedInUser user,
                                                 @Valid @RequestBody CreatePersonRequest request) {
        var created = withCompanyErrors(() -> service.create(user.id(), request));
        return ResponseEntity.created(URI.create("/api/people/" + created.id())).body(created);
    }

    @Operation(operationId = "getPerson")
    @GetMapping("/api/people/{id}")
    public ResponseEntity<PersonResponse> get(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(service.get(user.id(), id));
    }

    @Operation(operationId = "updatePerson")
    @PutMapping("/api/people/{id}")
    public ResponseEntity<PersonResponse> update(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                 @Valid @RequestBody UpdatePersonRequest request) {
        return ResponseEntity.of(withCompanyErrors(() -> service.update(user.id(), id, request)));
    }

    @Operation(operationId = "deletePerson")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/api/people/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return service.delete(user.id(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(operationId = "listPersonApplications")
    @GetMapping("/api/people/{id}/applications")
    public ResponseEntity<List<PersonApplicationResponse>> applications(@AuthenticationPrincipal SignedInUser user,
                                                                        @PathVariable UUID id) {
        return ResponseEntity.of(service.applicationsOf(user.id(), id));
    }

    @Operation(operationId = "listCompanyPeople")
    @GetMapping("/api/companies/{companyId}/people")
    public ResponseEntity<List<PersonResponse>> atCompany(@AuthenticationPrincipal SignedInUser user,
                                                          @PathVariable UUID companyId) {
        return ResponseEntity.of(service.atCompany(user.id(), companyId));
    }

    /** Another user's company id reads as unknown, the same as one that doesn't exist. */
    private static <T> T withCompanyErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (CompanyService.UnknownCompanyException ex) {
            throw new RequestValidationException("companyId", ex.getMessage());
        }
    }
}
