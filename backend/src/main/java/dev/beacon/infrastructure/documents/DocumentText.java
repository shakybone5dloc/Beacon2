package dev.beacon.infrastructure.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Plain text out of the document types that have it readily available. */
public final class DocumentText {

    private DocumentText() {}

    /** Empty for types without extractable text (Word files, images) or unreadable PDFs. */
    public static Optional<String> extract(byte[] content, String contentType) {
        var text = switch (contentType) {
            case "application/pdf" -> pdfText(content);
            case "text/plain", "text/markdown" -> new String(content, StandardCharsets.UTF_8);
            default -> null;
        };
        if (text == null) return Optional.empty();

        // Collapse the blank-line runs PDF extraction tends to leave behind.
        text = text.replace("\r\n", "\n").replaceAll("[ \\t]+\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String pdfText(byte[] pdf) {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            return null;
        }
    }
}
