package dev.beacon.application.coverletters;

import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.documents.PdfPrinter;
import dev.beacon.application.documents.PostingCaptureException;
import dev.beacon.contracts.documents.DocumentResponse;
import dev.beacon.domain.documents.DocumentKind;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Drafts a cover letter from an application's job description and a resume, and saves it as a PDF. */
@Service
public class CoverLetterService {

    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);

    private final JobApplicationRepository applications;
    private final DocumentService documents;
    private final CoverLetterWriter writer;
    private final PdfPrinter printer;
    private final Clock clock;

    public CoverLetterService(JobApplicationRepository applications, DocumentService documents, CoverLetterWriter writer,
                              PdfPrinter printer, Clock clock) {
        this.applications = applications;
        this.documents = documents;
        this.writer = writer;
        this.printer = printer;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return writer.isConfigured();
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

        return Optional.of(writer.draft(new CoverLetterWriter.CoverLetterInput(
                application.getCompany(), application.getRole(), application.getJobDescription(), resumeText, instructions)));
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

        var filename = "cover-letter-" + slug(application.getCompany()) + "-" + LocalDate.now(clock) + ".pdf";
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

    private static String slug(String text) {
        var slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) return "job";
        return slug.length() <= 60 ? slug : slug.substring(0, 60);
    }
}
