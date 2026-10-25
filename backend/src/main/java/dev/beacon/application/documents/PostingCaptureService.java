package dev.beacon.application.documents;

import dev.beacon.contracts.documents.CaptureResponse;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.documents.DocumentKind;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.DocumentRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Saves an application's job posting page as a PDF and keeps its text as the job description. */
@Service
public class PostingCaptureService {

    static final int MAX_DESCRIPTION = 100_000;
    static final int MIN_DESCRIPTION = 200;

    private final JobApplicationRepository applications;
    private final DocumentRepository documents;
    private final PdfPrinter printer;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public PostingCaptureService(JobApplicationRepository applications, DocumentRepository documents, PdfPrinter printer,
                                 PlatformTransactionManager transactionManager, Clock clock) {
        this.applications = applications;
        this.documents = documents;
        this.printer = printer;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Empty when the application does not exist; throws {@link PostingCaptureException} when the page can't be saved. */
    public Optional<CaptureResponse> capture(UUID ownerId, UUID applicationId) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();

        // Printing takes seconds, so it happens outside any transaction.
        var url = PublicUrls.require(found.get().getJobUrl());
        var pdf = printer.printUrl(url);
        var text = DocumentText.extract(pdf, "application/pdf");

        if (text.isPresent() && BotChecks.looksLikeBotCheck(text.get())) {
            throw new PostingCaptureException("The job site showed a bot check instead of the posting, so nothing was saved. "
                    + "Open the posting in your browser, print it to PDF and upload it here.", true);
        }

        return Optional.of(transaction.execute(status -> {
            var application = applications.findByIdAndOwnerId(applicationId, ownerId).orElseThrow(() ->
                    new PostingCaptureException("The application was deleted while its posting was being saved.", false));
            var now = Instant.now(clock);

            var filename = "posting-" + slug(application.getCompany()) + "-" + LocalDate.now(clock) + ".pdf";
            var document = Document.create(ownerId, applicationId, DocumentKind.POSTING, filename, "application/pdf", pdf, now);
            documents.save(document);

            // Never overwrite a description the user already has, and skip pages with barely any
            // text (usually a posting that renders as images or failed to load).
            var descriptionSaved = application.getJobDescription() == null
                    && text.filter(t -> t.length() >= MIN_DESCRIPTION).isPresent();
            if (descriptionSaved) {
                var description = DocumentText.reflow(text.get());
                application.setJobDescription(
                        description.length() <= MAX_DESCRIPTION ? description : description.substring(0, MAX_DESCRIPTION), now);
            }
            return new CaptureResponse(DocumentService.toResponse(document.info()), descriptionSaved);
        }));
    }

    static String slug(String text) {
        var slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) return "job";
        return slug.length() <= 60 ? slug : slug.substring(0, 60);
    }
}
