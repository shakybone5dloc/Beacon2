package dev.beacon.application.documents;

import dev.beacon.contracts.documents.DocumentResponse;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.applications.PostingDetails;
import dev.beacon.domain.documents.DocumentKind;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Prints Beacon's own copy of a job posting, built from the stored details and
 * description. Works for postings whose sites block automated browsers.
 */
@Service
public class PostingSummaryService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final Map<String, String> SYMBOLS = Map.of("USD", "$", "CAD", "CA$", "AUD", "A$", "GBP", "£", "EUR", "€");

    private final JobApplicationRepository applications;
    private final DocumentService documents;
    private final PdfPrinter printer;
    private final Clock clock;

    public PostingSummaryService(JobApplicationRepository applications, DocumentService documents, PdfPrinter printer,
                                 Clock clock) {
        this.applications = applications;
        this.documents = documents;
        this.printer = printer;
        this.clock = clock;
    }

    /** Empty when the application does not exist. */
    public Optional<DocumentResponse> save(UUID applicationId) {
        var found = applications.findById(applicationId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();

        var pdf = printer.printHtml(postingHtml(application));
        var filename = "posting-" + PostingCaptureService.slug(application.getCompany()) + "-summary-" + LocalDate.now(clock) + ".pdf";
        return documents.addToApplication(applicationId, DocumentKind.POSTING, filename, "application/pdf", pdf);
    }

    static String postingHtml(JobApplication app) {
        var details = app.getPostingDetails();

        var facts = new ArrayList<String>();
        var location = location(details);
        if (location != null) facts.add(fact("Location", location));
        var salary = salary(details);
        if (salary != null) facts.add(fact("Salary", salary));
        if (details.postedOn() != null) facts.add(fact("Posted", DATE.format(details.postedOn())));
        if (app.getApplyBy() != null) facts.add(fact("Apply by", DATE.format(app.getApplyBy())));
        if (details.source() != null) facts.add(fact("Found on", details.source()));

        var original = app.getJobUrl() != null && app.getJobUrl().matches("(?i)^https?://.+")
                ? "<p class=\"original\">Original posting: <a href=\"%s\">%s</a></p>"
                        .formatted(escape(app.getJobUrl()), escape(app.getJobUrl()))
                : "";

        var description = app.getJobDescription() == null
                ? "<p class=\"empty\">No job description was saved for this posting.</p>"
                : Arrays.stream(app.getJobDescription().split("\\n\\s*\\n"))
                        .map(String::strip)
                        .filter(p -> !p.isEmpty())
                        .map(p -> "<p>" + escape(p).replace("\n", "<br>") + "</p>")
                        .collect(Collectors.joining("\n"));

        return """
                <!doctype html>
                <html><head><meta charset="utf-8"><title>%s at %s</title><style>
                  @page { size: Letter; margin: 0.75in; }
                  body { font-family: "Segoe UI", system-ui, -apple-system, Roboto, sans-serif; font-size: 10.5pt;
                         line-height: 1.55; color: #1f2328; }
                  h1 { font-size: 18pt; line-height: 1.25; margin: 0; }
                  .company { margin: 2pt 0 0; font-size: 12pt; font-weight: 600; color: #59636e; }
                  .facts { display: flex; flex-wrap: wrap; gap: 8pt 22pt; margin: 14pt 0; padding: 9pt 11pt;
                           border: 1px solid #d8dee4; border-radius: 6pt; }
                  .facts div { min-width: 110pt; }
                  .facts .label { font-size: 7.5pt; text-transform: uppercase; letter-spacing: 0.04em; color: #59636e; }
                  .facts .value { font-weight: 600; }
                  .original { font-size: 9pt; color: #59636e; word-break: break-all; }
                  .original a { color: inherit; }
                  .description p { margin: 0 0 9pt; break-inside: avoid; }
                  .empty { color: #59636e; }
                </style></head><body>
                <h1>%s</h1>
                <p class="company">%s</p>
                %s
                %s
                <div class="description">
                %s
                </div>
                </body></html>
                """.formatted(
                escape(app.getRole()), escape(app.getCompany()),
                escape(app.getRole()),
                escape(app.getCompany()),
                facts.isEmpty() ? "" : "<div class=\"facts\">" + String.join("", facts) + "</div>",
                original,
                description);
    }

    private static String fact(String label, String value) {
        return "<div><div class=\"label\">%s</div><div class=\"value\">%s</div></div>".formatted(label, escape(value));
    }

    private static String location(PostingDetails d) {
        var arrangement = d.remote() == null ? null : d.remote() ? "Remote" : "On-site or hybrid";
        if (d.location() == null) return arrangement;
        return arrangement == null ? d.location() : d.location() + " · " + arrangement;
    }

    /** e.g. "$140,000 – $170,000 per year", or the ad's own wording when it gave no numbers. */
    static String salary(PostingDetails d) {
        if (d.salaryMin() == null && d.salaryMax() == null) return d.salaryText();

        var symbol = d.salaryCurrency() == null ? "" : SYMBOLS.getOrDefault(d.salaryCurrency(), d.salaryCurrency() + " ");
        var numbers = NumberFormat.getIntegerInstance(Locale.US);
        var range = d.salaryMin() != null && d.salaryMax() != null && !d.salaryMin().equals(d.salaryMax())
                ? symbol + numbers.format(d.salaryMin()) + " – " + symbol + numbers.format(d.salaryMax())
                : symbol + numbers.format(d.salaryMin() != null ? d.salaryMin() : d.salaryMax());
        return d.salaryPeriod() == null ? range : range + " per " + d.salaryPeriod();
    }

    /** With a charset given, only markup characters are escaped; "·" and "–" stay as they are. */
    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
