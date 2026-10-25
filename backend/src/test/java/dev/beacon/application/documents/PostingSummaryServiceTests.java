package dev.beacon.application.documents;

import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.applications.PostingDetails;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PostingSummaryServiceTests {

    private static final UUID OWNER = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    private static PostingDetails salary(Integer min, Integer max, String currency, String period, String text) {
        return new PostingDetails(null, null, min, max, currency, period, text, null, null);
    }

    @Test
    void salaryReadsLikeAPosting() {
        assertThat(PostingSummaryService.salary(salary(140000, 170000, "USD", "year", null)))
                .isEqualTo("$140,000 – $170,000 per year");
        assertThat(PostingSummaryService.salary(salary(45, 45, "USD", "hour", null))).isEqualTo("$45 per hour");
        assertThat(PostingSummaryService.salary(salary(null, 90000, "INR", null, null))).isEqualTo("INR 90,000");
        assertThat(PostingSummaryService.salary(salary(null, null, null, null, "DOE"))).isEqualTo("DOE");
    }

    @Test
    void htmlShowsTheFactsAndEscapesEverything() {
        var app = JobApplication.create(OWNER, "Acme <Labs>", "Senior Java Engineer", "https://acme.dev/jobs/1", null, NOW);
        app.setPostingDetails(new PostingDetails("Denver, CO", true, 140000, 170000, "USD", "year", null,
                "Himalayas", LocalDate.parse("2026-10-20")), NOW);
        app.setApplyBy(LocalDate.parse("2026-11-15"), NOW);
        app.setJobDescription("About us\n\nWe <build> tools.\nLine two", NOW);

        var html = PostingSummaryService.postingHtml(app);

        assertThat(html).contains("<h1>Senior Java Engineer</h1>");
        assertThat(html).contains("Acme &lt;Labs&gt;");
        assertThat(html).contains("Denver, CO · Remote");
        assertThat(html).contains("$140,000 – $170,000 per year");
        assertThat(html).contains("Oct 20, 2026").contains("Nov 15, 2026").contains("Himalayas");
        assertThat(html).contains("<a href=\"https://acme.dev/jobs/1\">");
        assertThat(html).contains("<p>About us</p>");
        assertThat(html).contains("<p>We &lt;build&gt; tools.<br>Line two</p>");
    }

    @Test
    void htmlLeavesOutNonWebLinksAndSaysWhenThereIsNoDescription() {
        var app = JobApplication.create(OWNER, "Acme", "Engineer", "javascript:alert(1)", null, NOW);

        var html = PostingSummaryService.postingHtml(app);

        assertThat(html).doesNotContain("javascript:");
        assertThat(html).contains("No job description was saved");
        assertThat(html).doesNotContain("class=\"facts\"");
    }
}
