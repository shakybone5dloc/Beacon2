package dev.beacon.api.searches;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.searches.SavedSearchRunner;
import dev.beacon.application.searches.SavedSearchService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.searches.SaveSearchRequest;
import dev.beacon.contracts.searches.SavedSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Saved searches: what to look for, when to look, and which resume to score matches against. */
@Tag(name = "Saved Searches")
@RestController
@RequestMapping("/api/saved-searches")
public class SavedSearchController {

    private final SavedSearchService searches;
    private final SavedSearchRunner runner;

    public SavedSearchController(SavedSearchService searches, SavedSearchRunner runner) {
        this.searches = searches;
        this.runner = runner;
    }

    @Operation(operationId = "listSavedSearches")
    @GetMapping
    public List<SavedSearchResponse> list(@AuthenticationPrincipal SignedInUser user) {
        return searches.list(user.id());
    }

    @Operation(operationId = "getSavedSearch")
    @GetMapping("/{id}")
    public ResponseEntity<SavedSearchResponse> get(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(searches.get(user.id(), id));
    }

    @Operation(operationId = "createSavedSearch")
    @ApiResponse(responseCode = "201")
    @PostMapping
    public ResponseEntity<SavedSearchResponse> create(@AuthenticationPrincipal SignedInUser user,
                                                      @Valid @RequestBody SaveSearchRequest request) {
        try {
            var created = searches.create(user.id(), request);
            return ResponseEntity.created(URI.create("/api/saved-searches/" + created.id())).body(created);
        } catch (SavedSearchService.InvalidSearchException ex) {
            throw new RequestValidationException(ex.getField(), ex.getMessage());
        }
    }

    @Operation(operationId = "updateSavedSearch")
    @PutMapping("/{id}")
    public ResponseEntity<SavedSearchResponse> update(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                      @Valid @RequestBody SaveSearchRequest request) {
        try {
            return ResponseEntity.of(searches.update(user.id(), id, request));
        } catch (SavedSearchService.InvalidSearchException ex) {
            throw new RequestValidationException(ex.getField(), ex.getMessage());
        }
    }

    @Operation(operationId = "deleteSavedSearch")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return searches.delete(user.id(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Starts a run now; it carries on after the response, so poll the search until
     * {@code running} is false. 409 when a run is already under way.
     */
    @Operation(operationId = "runSavedSearch")
    @ApiResponse(responseCode = "202")
    @PostMapping("/{id}/run")
    public ResponseEntity<SavedSearchResponse> run(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        var started = runner.runNow(user.id(), id);
        if (started.isEmpty()) return ResponseEntity.notFound().build();
        var status = started.get() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(searches.get(user.id(), id).orElseThrow());
    }
}
