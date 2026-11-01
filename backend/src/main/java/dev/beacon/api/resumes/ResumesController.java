package dev.beacon.api.resumes;

import dev.beacon.application.resumes.ResumeService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.resumes.ResumeResponse;
import dev.beacon.contracts.resumes.ResumeTextResponse;
import dev.beacon.contracts.resumes.UpdateResumeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The resumes in the user's library, with a label, a default and a short summary.
 * Uploading and deleting go through the library documents endpoints.
 */
@Tag(name = "Resumes")
@RestController
@RequestMapping("/api/resumes")
public class ResumesController {

    private final ResumeService service;

    public ResumesController(ResumeService service) {
        this.service = service;
    }

    @Operation(operationId = "listResumes")
    @GetMapping
    public List<ResumeResponse> list(@AuthenticationPrincipal SignedInUser user) {
        return service.list(user.id());
    }

    @Operation(operationId = "updateResume")
    @PutMapping("/{documentId}")
    public ResponseEntity<ResumeResponse> update(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID documentId,
                                                 @Valid @RequestBody UpdateResumeRequest request) {
        return ResponseEntity.of(service.update(user.id(), documentId, request));
    }

    @Operation(operationId = "makeDefaultResume")
    @PutMapping("/{documentId}/default")
    public ResponseEntity<ResumeResponse> makeDefault(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID documentId) {
        return ResponseEntity.of(service.makeDefault(user.id(), documentId));
    }

    /** What Beacon read from the file: the text cover letters and fit scoring work from. */
    @Operation(operationId = "getResumeText")
    @GetMapping("/{documentId}/text")
    public ResponseEntity<ResumeTextResponse> text(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID documentId) {
        return ResponseEntity.of(service.text(user.id(), documentId).map(text -> new ResumeTextResponse(text.orElse(null))));
    }
}
