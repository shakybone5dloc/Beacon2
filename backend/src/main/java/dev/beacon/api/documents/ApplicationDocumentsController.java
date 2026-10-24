package dev.beacon.api.documents;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.documents.PostingCaptureService;
import dev.beacon.contracts.documents.CaptureResponse;
import dev.beacon.contracts.documents.DocumentResponse;
import org.springframework.http.ResponseEntity;
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

    public ApplicationDocumentsController(DocumentService documents, PostingCaptureService capture) {
        this.documents = documents;
        this.capture = capture;
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> list(@PathVariable UUID applicationId) {
        return ResponseEntity.of(documents.listForApplication(applicationId));
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID applicationId,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "OTHER") String kind
    ) {
        var upload = DocumentFiles.validate(file, kind);
        return documents.addToApplication(applicationId, upload.kind(), upload.filename(), upload.contentType(), upload.content())
                .map(saved -> ResponseEntity
                        .created(URI.create("/api/applications/" + applicationId + "/documents/" + saved.id() + "/content"))
                        .body(saved))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Prints the application's job URL to PDF and attaches it as a POSTING document. */
    @PostMapping("/posting-capture")
    public ResponseEntity<CaptureResponse> capturePosting(@PathVariable UUID applicationId) {
        return ResponseEntity.of(capture.capture(applicationId));
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID applicationId, @PathVariable UUID documentId) {
        return documents.find(applicationId, documentId)
                .map(DocumentFiles::download)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID applicationId, @PathVariable UUID documentId) {
        return documents.delete(applicationId, documentId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
