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

    /**
     * Undoes the hard line wrapping PDF text comes with. Lines that run close to
     * the page width were wrapped and get joined; a shorter line ending a sentence
     * ends a paragraph; bullets and headings ending in ":" keep their own lines.
     */
    public static String reflow(String text) {
        var lines = text.replace("\r\n", "\n").split("\n");
        var longest = 0;
        for (var line : lines) longest = Math.max(longest, line.strip().length());
        if (longest < 40) return text;
        var wrapWidth = longest * 0.75;

        var out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            var line = lines[i].strip();
            out.append(line);
            if (i == lines.length - 1) break;

            var next = lines[i + 1].strip();
            if (line.isEmpty() || next.isEmpty()) {
                out.append('\n');
            } else if (next.endsWith(":") && next.length() < wrapWidth) {
                // A short line ending in ":" is a heading like "What you'll do:".
                out.append("\n\n");
            } else if (line.length() >= wrapWidth && !line.endsWith(":") && !isBullet(next)) {
                out.append(' ');
            } else if (line.matches(".*[.!?]$") && !isBullet(next)) {
                out.append("\n\n");
            } else {
                out.append('\n');
            }
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    private static boolean isBullet(String line) {
        return line.matches("^([•\\-*–·]|\\d+[.)])\\s.*");
    }

    private static String pdfText(byte[] pdf) {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException ex) {
            return null;
        }
    }
}
