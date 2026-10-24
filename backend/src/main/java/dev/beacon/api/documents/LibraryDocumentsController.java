package dev.beacon.api.documents;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.contracts.documents.DocumentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Documents reused across applications, such as a main resume. */
@RestController
@RequestMapping("/api/documents")
public class LibraryDocumentsController {

    private final DocumentService documents;

    public LibraryDocumentsController(DocumentService documents) {
        this.documents = documents;
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documents.listLibrary();
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(defaultValue = "OTHER") String kind
    ) {
        var upload = DocumentFiles.validate(file, kind);
        var saved = documents.addToLibrary(upload.kind(), upload.filename(), upload.contentType(), upload.content());
        return ResponseEntity.created(URI.create("/api/documents/" + saved.id() + "/content")).body(saved);
    }

    @GetMapping("/{documentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID documentId) {
        return documents.find(null, documentId)
                .map(DocumentFiles::download)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        return documents.delete(null, documentId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
