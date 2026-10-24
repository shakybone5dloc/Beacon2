package dev.beacon.application.documents;

import java.net.URI;

/** Turns web pages and HTML into PDFs. Failures throw {@link PostingCaptureException}. */
public interface PdfPrinter {

    byte[] printUrl(URI url);

    byte[] printHtml(String html);
}
