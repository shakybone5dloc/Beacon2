package dev.beacon;

import dev.beacon.application.briefs.BriefException;
import dev.beacon.application.briefs.BriefWriter;
import dev.beacon.application.briefs.CompanyResearcher;
import dev.beacon.application.coverletters.CoverLetterWriter;
import dev.beacon.application.digest.Mailer;
import dev.beacon.application.stories.StoryWriter;
import dev.beacon.application.study.CardWriter;
import dev.beacon.application.tailoring.ResumeTailor;
import dev.beacon.domain.tailoring.ResumeSuggestion;
import dev.beacon.application.documents.PdfPrinter;
import dev.beacon.application.documents.PostingCaptureException;
import dev.beacon.application.fit.FitScorer;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiUsage;
import dev.beacon.application.usage.AiUsageMeter;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Stand-ins for the headless browser and the AI writers and scorer, so tests never call them. */
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

    @Bean
    FakeFitScorer fakeFitScorer() {
        return new FakeFitScorer();
    }

    @Bean
    FakeBriefWriter fakeBriefWriter() {
        return new FakeBriefWriter();
    }

    @Bean
    FakeCompanyResearcher fakeCompanyResearcher() {
        return new FakeCompanyResearcher();
    }

    @Bean
    FakeStoryWriter fakeStoryWriter() {
        return new FakeStoryWriter();
    }

    @Bean
    FakeResumeTailor fakeResumeTailor() {
        return new FakeResumeTailor();
    }

    @Bean
    @Primary
    FakeMailer fakeMailer() {
        return new FakeMailer();
    }

    /** Keeps what would have been emailed. Set {@code configured} false to act like a server with no mail set up. */
    public static class FakeMailer implements Mailer {
        public record Sent(String to, String subject, String html, String text) {}

        public final List<Sent> sent = new ArrayList<>();
        public boolean configured = true;

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public void send(String to, String subject, String html, String text) {
            sent.add(new Sent(to, subject, html, text));
        }

        public void reset() {
            sent.clear();
            configured = true;
        }
    }

    public static class FakeResumeTailor implements ResumeTailor, Ordered {
        public final List<TailorInput> inputs = new ArrayList<>();

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public List<Suggestion> suggest(TailorInput input, String apiKey, AiUsageMeter meter) {
            meter.record(FakeCoverLetterWriter.USAGE);
            inputs.add(input);
            var round = inputs.size();
            return List.of(
                    new Suggestion(ResumeSuggestion.Kind.REWORD, "Experience: Acme",
                            "Say \"Spring Boot\" rather than \"Spring\" (round " + round + ").",
                            "The posting asks for Spring Boot by name.", "Built REST APIs in Spring Boot 3."),
                    new Suggestion(ResumeSuggestion.Kind.MOVE, "Skills",
                            "Move the skills section above education.", "Recruiters scan for the stack first.", null));
        }

        public void reset() {
            inputs.clear();
        }
    }

    @Bean
    FakeCardWriter fakeCardWriter() {
        return new FakeCardWriter();
    }

    /** Stands in for OpenAI's study cards: two cards, the same each time. */
    public static class FakeCardWriter implements CardWriter, Ordered {
        public final List<CardInput> inputs = new ArrayList<>();

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public List<CardDraft> write(CardInput input, String apiKey, AiUsageMeter meter) {
            meter.record(FakeCoverLetterWriter.USAGE);
            inputs.add(input);
            return List.of(new CardDraft("What is OAuth?", "- Delegated authorization"),
                    new CardDraft("Your 30-second pitch", "- Java, Spring, Kafka"));
        }

        public void reset() {
            inputs.clear();
        }
    }

    /** Stands in for OpenAI's story drafts; one story from notes, two from a resume. */
    public static class FakeStoryWriter implements StoryWriter, Ordered {
        public final List<DraftInput> inputs = new ArrayList<>();

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public List<Draft> draft(DraftInput input, String apiKey, AiUsageMeter meter) {
            meter.record(FakeCoverLetterWriter.USAGE);
            inputs.add(input);
            var fromNotes = new Draft("Cut checkout latency", "Checkout was slow.", "Make it fast.",
                    "Profiled and cached the price lookups.", "Checkout got faster.", "[how much faster?]",
                    List.of("Performance ", "performance", "hardest problem"));
            return input.notes() != null ? List.of(fromNotes)
                    : List.of(fromNotes, new Draft("Mentored two juniors", "", "", "Paired weekly.",
                            "Both promoted.", "", List.of("mentoring")));
        }

        public void reset() {
            inputs.clear();
        }
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
        /** What each draft reports using: $0.012 at gpt-6-sol prices. */
        public static final AiUsage USAGE = new AiUsage(ProviderCatalog.OPENAI, "gpt-6-sol", 2000, 0, 800);

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
        public String draft(CoverLetterInput input, String apiKey, AiUsageMeter meter) {
            meter.record(USAGE);
            lastInput = input;
            lastApiKey = apiKey;
            return "Dear Hiring Team,\n\nI would like to join " + input.company() + ".";
        }
    }

    /** Stands in for OpenAI's scorer; counts calls so tests can see when a kept score was reused. */
    public static class FakeFitScorer implements FitScorer, Ordered {
        /** What each score reports using: $0.00664 at gpt-6-sol prices (200 of the input tokens cached). */
        public static final AiUsage USAGE = new AiUsage(ProviderCatalog.OPENAI, "gpt-6-sol", 1000, 200, 500);

        public final List<FitInput> inputs = new ArrayList<>();
        public String lastApiKey;
        public LocalDate applyBy;

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public FitResult score(FitInput input, String apiKey, AiUsageMeter meter) {
            meter.record(USAGE);
            inputs.add(input);
            lastApiKey = apiKey;
            return new FitResult(82, List.of("Java", "Spring Boot"), List.of("Kubernetes"), List.of(),
                    "Strong Java background; no Kubernetes shown.", applyBy);
        }

        public void reset() {
            inputs.clear();
            lastApiKey = null;
            applyBy = null;
        }
    }

    /** Stands in for OpenAI's brief writer; keeps every input so tests can see what it was given. */
    public static class FakeBriefWriter implements BriefWriter, Ordered {
        /** What each section reports using. */
        public static final AiUsage USAGE = new AiUsage(ProviderCatalog.OPENAI, "gpt-6-sol", 3000, 0, 1200);

        public final List<BriefInput> inputs = new ArrayList<>();
        public String failWith;

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public String model() {
            return "fake-model";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public String write(BriefInput input, String apiKey, AiUsageMeter meter) {
            meter.record(USAGE);
            inputs.add(input);
            if (failWith != null) throw new BriefException(failWith, true);
            return "Draft of " + input.section().title() + " for " + input.role() + ".";
        }

        public void reset() {
            inputs.clear();
            failWith = null;
        }
    }

    /** Stands in for OpenAI's web research; reports a call that ran four searches. */
    public static class FakeCompanyResearcher implements CompanyResearcher, Ordered {
        public static final AiUsage USAGE = new AiUsage(ProviderCatalog.OPENAI, "gpt-6-sol", 20000, 0, 900, 4);

        public final List<BriefWriter.BriefInput> inputs = new ArrayList<>();

        @Override
        public String provider() {
            return ProviderCatalog.OPENAI;
        }

        @Override
        public String model() {
            return "fake-research-model";
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public String research(BriefWriter.BriefInput input, String apiKey, AiUsageMeter meter) {
            meter.record(USAGE);
            inputs.add(input);
            return """
                    ### Snapshot

                    %s runs a supply chain network. [[1]](https://example.com/about)

                    ### Sources

                    1. [About](https://example.com/about)""".formatted(input.company());
        }

        public void reset() {
            inputs.clear();
        }
    }
}
