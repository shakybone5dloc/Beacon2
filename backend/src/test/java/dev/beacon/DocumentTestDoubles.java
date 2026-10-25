package dev.beacon;

import dev.beacon.application.coverletters.CoverLetterWriter;
import dev.beacon.application.documents.PdfPrinter;
import dev.beacon.application.documents.PostingCaptureException;
import dev.beacon.application.providers.ProviderCatalog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Stand-ins for the headless browser and the AI writer, so tests never call either. */
@TestConfiguration(proxyBeanMethods = false)
public class DocumentTestDoubles {

    @Bean
    @Primary
    FakePdfPrinter fakePdfPrinter() {
        return new FakePdfPrinter();
    }

    @Bean
    FakeCoverLetterWriter fakeCoverLetterWriter() {
        return new FakeCoverLetterWriter();
    }

    /** Builds a one-page PDF containing the given lines, the way a printed posting would. */
    public static byte[] pdfWithText(String... lines) {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setLeading(16);
                content.newLineAtOffset(72, 720);
                for (var line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static class FakePdfPrinter implements PdfPrinter {
        /** What a printed posting says; long enough to count as a real description. */
        public static final String[] POSTING_LINES = {
                "Senior Java Engineer",
                "Build services with Spring Boot.",
                "You will design and run the APIs behind our scheduling product,",
                "work closely with product and design, and mentor two junior engineers.",
                "We use Java 21, Spring Boot, Postgres and Kubernetes."
        };

        public final List<URI> printedUrls = new ArrayList<>();
        public String[] pageLines = POSTING_LINES;
        public final List<String> printedHtml = new ArrayList<>();
        public String failWith;

        @Override
        public byte[] printUrl(URI url) {
            if (failWith != null) throw new PostingCaptureException(failWith, true);
            printedUrls.add(url);
            return pdfWithText(pageLines);
        }

        @Override
        public byte[] printHtml(String html) {
            if (failWith != null) throw new PostingCaptureException(failWith, true);
            printedHtml.add(html);
            return pdfWithText("Cover letter");
        }

        public void reset() {
            printedUrls.clear();
            printedHtml.clear();
            failWith = null;
            pageLines = POSTING_LINES;
        }
    }

    /** Stands in for OpenAI; ordered first so it wins over the real writer for the same provider. */
    public static class FakeCoverLetterWriter implements CoverLetterWriter, Ordered {
        public CoverLetterInput lastInput;
        public String lastApiKey;

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public String draft(CoverLetterInput input, String apiKey) {
            lastInput = input;
            lastApiKey = apiKey;
            return "Dear Hiring Team,\n\nI would like to join " + input.company() + ".";
        }
    }
}
