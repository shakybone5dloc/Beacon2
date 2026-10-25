package dev.beacon.api.documents;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.documents.PostingCaptureService;
import dev.beacon.application.documents.PostingSummaryService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.documents.CaptureResponse;
import dev.beacon.contracts.documents.DocumentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications/{applicationId}/documents")
public class ApplicationDocumentsController {

    private final DocumentService documents;
    private final PostingCaptureService capture;
    private final PostingSummaryService summary;

    public ApplicationDocumentsController(DocumentService documents, PostingCaptureService capture,
                                          PostingSummaryService summary) {
        this.documents = documents;
        this.capture = capture;
        this.summary = summary;
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return ResponseEntity.of(documents.listForApplication(user.id(), applicationId));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID applicationId,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "OTHER") String kind
    ) {
        var upload = DocumentFiles.validate(file, kind);
        return documents.addToApplication(user.id(), applicationId, upload.kind(), upload.filename(), upload.contentType(), upload.content())
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/applications/" + applicationId + "/documents/" + saved.id() + "/content"))
                        .body(saved))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Prints the application's job URL to PDF and attaches it as a POSTING document. */
    @PostMapping("/posting-capture")
    public ResponseEntity<CaptureResponse> capturePosting(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return ResponseEntity.of(capture.capture(user.id(), applicationId));
    }

    /** Prints Beacon's own copy of the posting, from the stored details, as a POSTING document. */
    @PostMapping("/posting-summary")
    public ResponseEntity<DocumentResponse> savePostingSummary(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return summary.save(user.id(), applicationId)
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/applications/" + applicationId + "/documents/" + saved.id() + "/content"))
                        .body(saved))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId, @PathVariable UUID documentId) {
        return documents.find(user.id(), applicationId, documentId)
                .map(DocumentFiles::download)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId, @PathVariable UUID documentId) {
        return documents.delete(user.id(), applicationId, documentId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
