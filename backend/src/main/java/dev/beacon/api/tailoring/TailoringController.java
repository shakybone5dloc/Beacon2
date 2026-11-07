package dev.beacon.api.tailoring;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.tailoring.TailoringService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.tailoring.MarkSuggestionRequest;
import dev.beacon.contracts.tailoring.ResumeSuggestionResponse;
import dev.beacon.contracts.tailoring.SentResumeRequest;
import dev.beacon.contracts.tailoring.SuggestResumeChangesRequest;
import dev.beacon.contracts.tailoring.TailoringResponse;
import dev.beacon.domain.tailoring.ResumeSuggestion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The resume sent with an application, and AI suggestions to tailor one to its posting (A2). */
@Tag(name = "Resume tailoring")
@RestController
@RequestMapping("/api/applications/{applicationId}/resume")
public class TailoringController {

    private final TailoringService tailoring;

    public TailoringController(TailoringService tailoring) {
        this.tailoring = tailoring;
    }

    @Operation(operationId = "getApplicationResume")
    @GetMapping
    public ResponseEntity<TailoringResponse> get(@AuthenticationPrincipal SignedInUser user,
                                                 @PathVariable UUID applicationId) {
        return ResponseEntity.of(tailoring.get(user.id(), applicationId));
    }

    /** Asks the AI for new suggestions; the ones marked done are kept, the rest replaced. */
    @Operation(operationId = "suggestResumeChanges")
    @PostMapping("/suggestions")
    public ResponseEntity<TailoringResponse> suggest(@AuthenticationPrincipal SignedInUser user,
                                                     @PathVariable UUID applicationId,
                                                     @Valid @RequestBody SuggestResumeChangesRequest body) {
        return ResponseEntity.of(tailoring.suggest(user.id(), applicationId, body.resumeDocumentId()));
    }

    @Operation(operationId = "markResumeSuggestion")
    @PutMapping("/suggestions/{suggestionId}")
    public ResponseEntity<ResumeSuggestionResponse> mark(@AuthenticationPrincipal SignedInUser user,
                                                         @PathVariable UUID applicationId,
                                                         @PathVariable UUID suggestionId,
                                                         @Valid @RequestBody MarkSuggestionRequest body) {
        ResumeSuggestion.State state;
        try {
            state = ResumeSuggestion.State.valueOf(body.state());
        } catch (IllegalArgumentException ex) {
            throw new RequestValidationException("state", "must be OPEN, DONE or DISMISSED");
        }
        return ResponseEntity.of(tailoring.mark(user.id(), applicationId, suggestionId, state));
    }

    @Operation(operationId = "setSentResume")
    @PutMapping("/sent")
    public ResponseEntity<TailoringResponse> setSent(@AuthenticationPrincipal SignedInUser user,
                                                     @PathVariable UUID applicationId,
                                                     @RequestBody SentResumeRequest body) {
        return ResponseEntity.of(tailoring.setSent(user.id(), applicationId, body.resumeDocumentId()));
    }
}
