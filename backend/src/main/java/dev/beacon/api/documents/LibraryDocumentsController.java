package dev.beacon.api.documents;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.users.SignedInUser;
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

/** Documents reused across applications, such as a main resume. */
@Tag(name = "Library documents")
@RestController
@RequestMapping("/api/documents")
public class LibraryDocumentsController {

    private final DocumentService documents;

    public LibraryDocumentsController(DocumentService documents) {
        this.documents = documents;
    }

    @Operation(operationId = "listLibraryDocuments")
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal SignedInUser user) {
        return documents.listLibrary(user.id());
    }

    @Operation(operationId = "uploadLibraryDocument")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal SignedInUser user,
            @RequestParam(required = false) MultipartFile file,
            @Parameter(schema = @Schema(implementation = DocumentKind.class)) @RequestParam(defaultValue = "OTHER") String kind
    ) {
        var upload = DocumentFiles.validate(file, kind);
        var saved = documents.addToLibrary(user.id(), upload.kind(), upload.filename(), upload.contentType(), upload.content());
        return ResponseEntity.created(URI.create("/api/documents/" + saved.id() + "/content")).body(saved);
    }

    @Operation(operationId = "downloadLibraryDocument")
    @ApiResponse(responseCode = "200", description = "The file, inline for PDFs and images",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID documentId) {
        return documents.find(user.id(), null, documentId)
                .map(DocumentFiles::download)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "deleteLibraryDocument")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID documentId) {
        return documents.delete(user.id(), null, documentId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
