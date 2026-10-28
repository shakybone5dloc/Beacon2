package dev.beacon.application.coverletters;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.documents.Filenames;
import dev.beacon.application.documents.PdfPrinter;
import dev.beacon.application.documents.PostingCaptureException;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.contracts.documents.DocumentResponse;
import dev.beacon.domain.documents.DocumentKind;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Drafts a cover letter from an application's job description and a resume, and saves it as a PDF. */
@Service
public class CoverLetterService {

    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);

    /** Tried in this order: a user with both keys gets OpenAI. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private final JobApplicationRepository applications;
    private final DocumentService documents;
    private final List<CoverLetterWriter> writers;
    private final UserCredentials credentials;
    private final PdfPrinter printer;
    private final Clock clock;

    public CoverLetterService(JobApplicationRepository applications, DocumentService documents,
                              List<CoverLetterWriter> writers, UserCredentials credentials, PdfPrinter printer, Clock clock) {
        this.applications = applications;
        this.documents = documents;
        this.writers = writers;
        this.credentials = credentials;
        this.printer = printer;
        this.clock = clock;
    }

    /** True when the user has saved a key for any AI provider Beacon can draft with. */
    public boolean isConfigured(UUID userId) {
        return chooseProvider(credentials.savedProviders(userId)).isPresent();
    }

    /**
     * Empty when the application does not exist. The resume may belong to this
     * application or to the library.
     */
    public Optional<String> draft(UUID ownerId, UUID applicationId, UUID resumeDocumentId, String instructions) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();

        if (application.getJobDescription() == null) {
            throw new CoverLetterException("Save the posting as a PDF or paste the job description into this application first.", false);
        }

        var resume = documents.find(ownerId, applicationId, resumeDocumentId)
                .or(() -> documents.find(ownerId, null, resumeDocumentId))
                .orElseThrow(() -> new CoverLetterException("That resume was not found.", false));
        var resumeText = DocumentText.extract(resume.getContent(), resume.getContentType())
                .orElseThrow(() -> new CoverLetterException(
                        "Beacon can read PDF, text and Markdown resumes. Upload your resume in one of those formats to draft from it.", false));

        var provider = chooseProvider(credentials.savedProviders(ownerId)).orElseThrow(() -> new CoverLetterException(
                "Add an OpenAI or Anthropic API key under Settings to draft cover letters.", false));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new CoverLetterException(ex.getMessage(), false);
        }

        return Optional.of(writer(provider).draft(new CoverLetterWriter.CoverLetterInput(
                application.getCompanyName(), application.getRole(), application.getJobDescription(), resumeText, instructions),
                apiKey));
    }

    private Optional<String> chooseProvider(Set<String> saved) {
        return PREFERENCE.stream()
                .filter(saved::contains)
                .filter(id -> writers.stream().anyMatch(w -> w.provider().equals(id)))
                .findFirst();
    }

    private CoverLetterWriter writer(String provider) {
        return writers.stream().filter(w -> w.provider().equals(provider)).findFirst().orElseThrow();
    }

    /** Empty when the application does not exist. */
    public Optional<DocumentResponse> save(UUID ownerId, UUID applicationId, String text) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();

        byte[] pdf;
        try {
            pdf = printer.printHtml(letterHtml(text, LocalDate.now(clock)));
        } catch (PostingCaptureException ex) {
            throw new CoverLetterException("Could not turn the letter into a PDF: " + ex.getMessage(), true);
        }

        var filename = "cover-letter-" + Filenames.slug(application.getEmployerLabel()) + "-" + LocalDate.now(clock) + ".pdf";
        return documents.addToApplication(ownerId, applicationId, DocumentKind.COVER_LETTER, filename, "application/pdf", pdf);
    }

    /** A plain business letter: date, then the text with blank lines as paragraph breaks. */
    static String letterHtml(String text, LocalDate date) {
        var paragraphs = Arrays.stream(text.strip().replace("\r\n", "\n").split("\n\\s*\n"))
                .map(p -> "<p>" + HtmlUtils.htmlEscape(p.strip()).replace("\n", "<br>") + "</p>")
                .collect(Collectors.joining("\n"));
        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><style>
                  @page { size: Letter; margin: 1in; }
                  body { font-family: Georgia, "Times New Roman", serif; font-size: 11.5pt; line-height: 1.5; color: #111; }
                  p { margin: 0 0 0.9em; }
                  .date { margin-bottom: 1.6em; }
                </style></head><body>
                <p class="date">%s</p>
                %s
                </body></html>
                """.formatted(LETTER_DATE.format(date), paragraphs);
    }
}
