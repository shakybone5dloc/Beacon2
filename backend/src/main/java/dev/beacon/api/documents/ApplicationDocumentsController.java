package dev.beacon.api.documents;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.documents.PostingCaptureService;
import dev.beacon.application.documents.PostingSummaryService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.documents.CaptureResponse;
import dev.beacon.contracts.documents.DocumentResponse;
import dev.beacon.domain.documents.DocumentKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Application documents")
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

    @Operation(operationId = "listApplicationDocuments")
    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return ResponseEntity.of(documents.listForApplication(user.id(), applicationId));
    }

    @Operation(operationId = "uploadApplicationDocument")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable UUID applicationId,
            @RequestParam(required = false) MultipartFile file,
            @Parameter(schema = @Schema(implementation = DocumentKind.class)) @RequestParam(defaultValue = "OTHER") String kind
    ) {
        var upload = DocumentFiles.validate(file, kind);
        return documents.addToApplication(user.id(), applicationId, upload.kind(), upload.filename(), upload.contentType(), upload.content())
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/applications/" + applicationId + "/documents/" + saved.id() + "/content"))
                        .body(saved))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Prints the application's job URL to PDF and attaches it as a POSTING document. */
    @Operation(operationId = "capturePosting")
    @PostMapping("/posting-capture")
    public ResponseEntity<CaptureResponse> capturePosting(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return ResponseEntity.of(capture.capture(user.id(), applicationId));
    }

    /** Prints Beacon's own copy of the posting, from the stored details, as a POSTING document. */
    @Operation(operationId = "savePostingSummary")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping("/posting-summary")
    public ResponseEntity<DocumentResponse> savePostingSummary(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return summary.save(user.id(), applicationId)
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/applications/" + applicationId + "/documents/" + saved.id() + "/content"))
                        .body(saved))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "downloadApplicationDocument")
    @ApiResponse(responseCode = "200", description = "The file, inline for PDFs and images",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId, @PathVariable UUID documentId) {
        return documents.find(user.id(), applicationId, documentId)
                .map(DocumentFiles::download)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "deleteApplicationDocument")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId, @PathVariable UUID documentId) {
        return documents.delete(user.id(), applicationId, documentId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
