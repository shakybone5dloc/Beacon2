package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingMatcher;
import dev.beacon.application.postings.PostingQuery;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Remote jobs from jobicy.com, covering the last seven days. Their guidance is
 * at most one sync an hour, so the latest page is cached and filtered locally.
 */
@Component
public class JobicySource implements JobSource {

    private static final Duration FEED_TTL = Duration.ofHours(1);

    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> feed;

    public JobicySource(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://jobicy.com").build();
        this.feed = new TtlCache<>(FEED_TTL, clock, 1);
    }

    @Override public String id() { return "jobicy"; }
    @Override public String name() { return "Jobicy"; }
    @Override public String coverage() { return "Remote jobs posted in the last seven days."; }
    @Override public String attributionUrl() { return "https://jobicy.com"; }

    @Override
    public List<JobPosting> search(PostingQuery query) {
        return feed.get("all", this::fetchAll).stream()
                .filter(p -> PostingMatcher.matches(p, query))
                .toList();
    }

    private List<JobPosting> fetchAll() {
        var body = http.get().uri("/api/jobs?count=200").retrieve().body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("jobs").values()) {
            var tags = new ArrayList<>(Feeds.strings(job, "jobIndustry"));
            tags.addAll(Feeds.strings(job, "jobType"));

            var published = Feeds.text(job, "pubDate");
            postings.add(new JobPosting(
                    id(),
                    Feeds.text(job, "id"),
                    Feeds.plain(Feeds.text(job, "jobTitle")),
                    Feeds.plain(Feeds.text(job, "companyName")),
                    Feeds.text(job, "jobGeo"),
                    true,
                    Feeds.text(job, "url"),
                    published == null ? null : OffsetDateTime.parse(published).toInstant(),
                    null,
                    Feeds.positiveInt(job, "salaryMin"),
                    Feeds.positiveInt(job, "salaryMax"),
                    Feeds.text(job, "salaryCurrency"),
                    period(Feeds.text(job, "salaryPeriod")),
                    null,
                    tags,
                    Feeds.snippet(Feeds.text(job, "jobExcerpt"))
            ));
        }
        return postings;
    }

    /** "yearly", "annual", "hourly" and friends become "year" or "hour". */
    private static String period(String value) {
        if (value == null) return null;
        var lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("year") || lower.startsWith("annual")) return "year";
        if (lower.startsWith("hour")) return "hour";
        if (lower.startsWith("month")) return "month";
        return lower;
    }
}
