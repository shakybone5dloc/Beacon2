package dev.beacon.api.documents;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.documents.DocumentKind;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Upload checks and download responses shared by the document endpoints. */
final class DocumentFiles {

    /**
     * Allowed file types by extension. The stored content type comes from here,
     * never from the browser, and HTML/SVG are absent so an upload can't run script.
     */
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"),
            Map.entry("rtf", "application/rtf"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"));

    /** Types a browser can safely show inline; everything else downloads. */
    private static final Set<String> INLINE = Set.of("application/pdf", "image/png", "image/jpeg", "text/plain");

    record Upload(DocumentKind kind, String filename, String contentType, byte[] content) {}

    private DocumentFiles() {}

    static Upload validate(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) {
            throw new RequestValidationException("file", "Choose a file to upload");
        }
        if (file.getSize() > Document.MAX_BYTES) {
            throw new RequestValidationException("file", "Files can be at most 10 MB");
        }

        var filename = cleanFilename(file.getOriginalFilename());
        var dot = filename.lastIndexOf('.');
        var extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        var contentType = TYPES.get(extension);
        if (contentType == null) {
            throw new RequestValidationException("file", "Upload a PDF, Word, OpenDocument, RTF, text, Markdown or image file");
        }

        try {
            return new Upload(parseKind(kind), filename, contentType, file.getBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    static DocumentKind parseKind(String kind) {
        try {
            return DocumentKind.valueOf(kind.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new RequestValidationException("kind", "Must be POSTING, RESUME, COVER_LETTER or OTHER");
        }
    }

    static ResponseEntity<byte[]> download(Document document) {
        var disposition = INLINE.contains(document.getContentType()) ? ContentDisposition.inline() : ContentDisposition.attachment();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition.filename(document.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.getContent());
    }

    /** Drops any path a browser sent and characters that misbehave in headers. */
    static String cleanFilename(String original) {
        var name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty()) name = "document";
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }
}
