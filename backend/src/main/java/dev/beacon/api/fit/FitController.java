package dev.beacon.api.fit;

import dev.beacon.application.fit.FitService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.fit.ApplicationFitResponse;
import dev.beacon.contracts.fit.FitResponse;
import dev.beacon.contracts.fit.FitStatusResponse;
import dev.beacon.contracts.fit.ScoreApplicationFitRequest;
import dev.beacon.contracts.fit.ScorePostingFitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Fit")
@RestController
public class FitController {

    private final FitService service;

    public FitController(FitService service) {
        this.service = service;
    }

    @Operation(operationId = "getFitStatus")
    @GetMapping("/api/fit/status")
    public FitStatusResponse status(@AuthenticationPrincipal SignedInUser user) {
        return new FitStatusResponse(service.isConfigured(user.id()));
    }

    @Operation(operationId = "getApplicationFit")
    @GetMapping("/api/applications/{applicationId}/fit")
    public ResponseEntity<ApplicationFitResponse> forApplication(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID applicationId
    ) {
        return ResponseEntity.of(service.forApplication(user.id(), applicationId));
    }

    /** Scores the application's posting against a resume; the same inputs again reuse the kept score. */
    @Operation(operationId = "scoreApplicationFit")
    @PostMapping("/api/applications/{applicationId}/fit")
    public ResponseEntity<FitResponse> scoreApplication(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID applicationId,
            @Valid @RequestBody ScoreApplicationFitRequest request
    ) {
        return ResponseEntity.of(service.scoreApplication(user.id(), applicationId, request.resumeDocumentId()));
    }

    /** Scores a job search result that hasn't been saved to the board. */
    @Operation(operationId = "scorePostingFit")
    @PostMapping("/api/fit")
    public FitResponse scorePosting(
            @AuthenticationPrincipal SignedInUser user,
            @Valid @RequestBody ScorePostingFitRequest request
    ) {
        return service.scorePosting(user.id(), request);
    }
}
