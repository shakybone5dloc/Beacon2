package dev.beacon.api.coverletters;

import dev.beacon.application.coverletters.CoverLetterService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.coverletters.CoverLetterDraftRequest;
import dev.beacon.contracts.coverletters.CoverLetterDraftResponse;
import dev.beacon.contracts.coverletters.CoverLetterStatusResponse;
import dev.beacon.contracts.coverletters.SaveCoverLetterRequest;
import dev.beacon.contracts.documents.DocumentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
public class CoverLetterController {

    private final CoverLetterService service;

    public CoverLetterController(CoverLetterService service) {
        this.service = service;
    }

    @GetMapping("/api/cover-letters/status")
    public CoverLetterStatusResponse status(@AuthenticationPrincipal SignedInUser user) {
        return new CoverLetterStatusResponse(service.isConfigured(user.id()));
    }

    /** Returns a draft for the user to edit; nothing is saved. */
    @PostMapping("/api/applications/{applicationId}/cover-letter/draft")
    public ResponseEntity<CoverLetterDraftResponse> draft(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID applicationId,
            @Valid @RequestBody CoverLetterDraftRequest request
    ) {
        return ResponseEntity.of(service.draft(user.id(), applicationId, request.resumeDocumentId(), request.instructions())
                .map(CoverLetterDraftResponse::new));
    }

    /** Saves the (possibly edited) letter as a PDF cover letter document. */
    @PostMapping("/api/applications/{applicationId}/cover-letter")
    public ResponseEntity<DocumentResponse> save(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID applicationId,
            @Valid @RequestBody SaveCoverLetterRequest request
    ) {
        return service.save(user.id(), applicationId, request.text())
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/applications/" + applicationId + "/documents/" + saved.id() + "/content"))
                        .body(saved))
                .orElse(ResponseEntity.notFound().build());
    }
}
