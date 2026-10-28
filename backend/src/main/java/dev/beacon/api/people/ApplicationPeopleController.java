package dev.beacon.api.people;

import dev.beacon.application.people.PeopleService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.people.ApplicationPersonResponse;
import dev.beacon.contracts.people.LinkPersonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Who is involved in an application: recruiters, interviewers, referrals... */
@Tag(name = "Application people")
@RestController
@RequestMapping("/api/applications/{applicationId}/people")
public class ApplicationPeopleController {

    private final PeopleService service;

    public ApplicationPeopleController(PeopleService service) {
        this.service = service;
    }

    @Operation(operationId = "listApplicationPeople")
    @GetMapping
    public ResponseEntity<List<ApplicationPersonResponse>> list(@AuthenticationPrincipal SignedInUser user,
                                                                @PathVariable UUID applicationId) {
        return ResponseEntity.of(service.onApplication(user.id(), applicationId));
    }

    /** Adds the person, or changes their role if they're already on it. */
    @Operation(operationId = "linkApplicationPerson")
    @PutMapping("/{personId}")
    public ResponseEntity<ApplicationPersonResponse> link(@AuthenticationPrincipal SignedInUser user,
                                                          @PathVariable UUID applicationId, @PathVariable UUID personId,
                                                          @RequestBody(required = false) LinkPersonRequest request) {
        var role = request == null ? null : request.role();
        return ResponseEntity.of(service.link(user.id(), applicationId, personId, role));
    }

    @Operation(operationId = "unlinkApplicationPerson")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{personId}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal SignedInUser user,
                                       @PathVariable UUID applicationId, @PathVariable UUID personId) {
        return service.unlink(user.id(), applicationId, personId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
