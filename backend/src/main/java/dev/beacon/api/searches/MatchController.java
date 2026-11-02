package dev.beacon.api.searches;

import dev.beacon.application.searches.MatchService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.applications.ApplicationResponse;
import dev.beacon.contracts.searches.JobMatchResponse;
import dev.beacon.contracts.searches.MatchSummaryResponse;
import dev.beacon.domain.searches.JobMatch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Postings found by saved searches, waiting to be saved to the board or dismissed. */
@Tag(name = "Matches")
@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matches;

    public MatchController(MatchService matches) {
        this.matches = matches;
    }

    /** @param status NEW when left out */
    @Operation(operationId = "listMatches")
    @GetMapping
    public List<JobMatchResponse> list(@AuthenticationPrincipal SignedInUser user,
                                       @RequestParam(defaultValue = "NEW") JobMatch.Status status) {
        return matches.list(user.id(), status);
    }

    @Operation(operationId = "getMatchSummary")
    @GetMapping("/summary")
    public MatchSummaryResponse summary(@AuthenticationPrincipal SignedInUser user) {
        return matches.summary(user.id());
    }

    /** Saves the match to the board and returns the new application. */
    @Operation(operationId = "saveMatch")
    @PostMapping("/{id}/save")
    public ResponseEntity<ApplicationResponse> save(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(matches.save(user.id(), id));
    }

    @Operation(operationId = "dismissMatch")
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<JobMatchResponse> dismiss(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(matches.dismiss(user.id(), id));
    }

    @Operation(operationId = "restoreMatch")
    @PostMapping("/{id}/restore")
    public ResponseEntity<JobMatchResponse> restore(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(matches.restore(user.id(), id));
    }
}
