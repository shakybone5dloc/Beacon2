package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingMatcher;
import dev.beacon.application.postings.PostingQuery;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * US federal government jobs from USAJOBS. Each user brings their own free key,
 * and the API requires the key's email address as the User-Agent. Every posting has a
 * real application close date.
 */
@Component
public class UsaJobsSource implements JobSource {

    private static final Duration QUERY_TTL = Duration.ofHours(1);
    private static final int RESULTS = 50;

    private final RestClient http;
    private final TtlCache<PostingQuery, List<JobPosting>> cache;

    public UsaJobsSource(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://data.usajobs.gov").build();
        this.cache = new TtlCache<>(QUERY_TTL, clock, 50);
    }

    @Override public String id() { return "usajobs"; }
    @Override public String name() { return "USAJOBS"; }
    @Override public String coverage() { return "US federal government jobs, with application deadlines."; }
    @Override public String attributionUrl() { return "https://www.usajobs.gov"; }

    @Override
    public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
        return cache.get(query, () -> fetch(query, credentials));
    }

    private List<JobPosting> fetch(PostingQuery query, Map<String, String> credentials) {
        var body = http.get()
                .uri(uri -> {
                    uri.path("/api/Search")
                            .queryParam("Keyword", query.keywords())
                            .queryParam("ResultsPerPage", RESULTS);
                    if (!query.location().isBlank()) uri.queryParam("LocationName", query.location());
                    if (query.remoteOnly()) uri.queryParam("RemoteIndicator", "True");
                    return uri.build();
                })
                .header(HttpHeaders.USER_AGENT, credentials.get("email"))
                .header("Authorization-Key", credentials.get("apiKey"))
                .retrieve()
                .body(JsonNode.class);

        var postings = new ArrayList<JobPosting>();
        for (var item : body.path("SearchResult").path("SearchResultItems").values()) {
            var job = item.path("MatchedObjectDescriptor");
            var title = Feeds.text(job, "PositionTitle");
            var agency = Feeds.text(job, "OrganizationName");
            var summary = Feeds.text(job.path("UserArea").path("Details"), "JobSummary");

            // USAJOBS matches keywords anywhere in the full announcement, which lets in
            // unrelated jobs; keep only those naming the keywords in title, agency or summary.
            if (!PostingMatcher.matchesKeywords(query.keywords(), Arrays.asList(title, agency, Feeds.plain(summary)))) {
                continue;
            }

            var location = Feeds.text(job, "PositionLocationDisplay");
            var pay = job.path("PositionRemuneration").path(0);
            var published = date(Feeds.text(job, "PublicationStartDate"));

            postings.add(new JobPosting(
                    id(),
                    Feeds.text(job, "PositionID"),
                    title,
                    agency,
                    location,
                    query.remoteOnly() || (location != null && location.toLowerCase(Locale.ROOT).contains("remote")),
                    Feeds.text(job, "PositionURI"),
                    published == null ? null : published.atStartOfDay().toInstant(ZoneOffset.UTC),
                    date(Feeds.text(job, "ApplicationCloseDate")),
                    Feeds.positiveInt(pay, "MinimumRange"),
                    Feeds.positiveInt(pay, "MaximumRange"),
                    "USD",
                    period(Feeds.text(pay, "Description")),
                    null,
                    List.of(),
                    Feeds.snippet(summary),
                    Feeds.description(summary)
            ));
        }
        return postings;
    }

    /** USAJOBS dates look like "2026-11-15T23:59:59.9970"; only the day matters here. */
    private static LocalDate date(String value) {
        return value == null || value.length() < 10 ? null : LocalDate.parse(value.substring(0, 10));
    }

    private static String period(String description) {
        if (description == null) return null;
        var lower = description.toLowerCase(Locale.ROOT);
        if (lower.contains("year")) return "year";
        if (lower.contains("hour")) return "hour";
        return null;
    }
}
