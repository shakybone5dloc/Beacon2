package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.config.PostingsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * US federal government jobs from USAJOBS. Needs a free key, and their API
 * requires the key's email address as the User-Agent. Every posting has a
 * real application close date.
 */
@Component
public class UsaJobsSource implements JobSource {

    private static final Duration QUERY_TTL = Duration.ofHours(1);
    private static final int RESULTS = 50;

    private final RestClient http;
    private final PostingsProperties.UsaJobs config;
    private final TtlCache<PostingQuery, List<JobPosting>> cache;

    public UsaJobsSource(RestClient.Builder builder, Clock clock, PostingsProperties properties) {
        this.config = properties.usajobs();
        if (config.configured()) {
            builder.defaultHeader(HttpHeaders.USER_AGENT, config.email().strip())
                    .defaultHeader("Authorization-Key", config.apiKey().strip());
        }
        this.http = builder.baseUrl("https://data.usajobs.gov").build();
        this.cache = new TtlCache<>(QUERY_TTL, clock, 50);
    }

    @Override public String id() { return "usajobs"; }
    @Override public String name() { return "USAJOBS"; }
    @Override public String coverage() { return "US federal government jobs, with application deadlines."; }
    @Override public String attributionUrl() { return "https://www.usajobs.gov"; }
    @Override public boolean isConfigured() { return config.configured(); }

    @Override
    public String setupHint() {
        return "Request a free key at developer.usajobs.gov, then set USAJOBS_API_KEY and USAJOBS_EMAIL in .env.";
    }

    @Override
    public List<JobPosting> search(PostingQuery query) {
        return cache.get(query, () -> fetch(query));
    }

    private List<JobPosting> fetch(PostingQuery query) {
        var body = http.get()
                .uri(uri -> {
                    uri.path("/api/Search")
                            .queryParam("Keyword", query.keywords())
                            .queryParam("ResultsPerPage", RESULTS);
                    if (!query.location().isBlank()) uri.queryParam("LocationName", query.location());
                    if (query.remoteOnly()) uri.queryParam("RemoteIndicator", "True");
                    return uri.build();
                })
                .retrieve()
                .body(JsonNode.class);

        var postings = new ArrayList<JobPosting>();
        for (var item : body.path("SearchResult").path("SearchResultItems").values()) {
            var job = item.path("MatchedObjectDescriptor");
            var location = Feeds.text(job, "PositionLocationDisplay");
            var pay = job.path("PositionRemuneration").path(0);
            var published = date(Feeds.text(job, "PublicationStartDate"));

            postings.add(new JobPosting(
                    id(),
                    Feeds.text(job, "PositionID"),
                    Feeds.text(job, "PositionTitle"),
                    Feeds.text(job, "OrganizationName"),
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
                    Feeds.snippet(Feeds.text(job.path("UserArea").path("Details"), "JobSummary"))
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
