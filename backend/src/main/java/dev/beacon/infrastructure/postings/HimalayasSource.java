package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingMatcher;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.config.PostingsProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Remote jobs from himalayas.app. Their search is loose (a search for "java"
 * returns designers too), so results also go through the local matcher.
 * Works without a key; a free key raises the rate limit.
 */
@Component
public class HimalayasSource implements JobSource {

    private static final Duration QUERY_TTL = Duration.ofHours(1);
    private static final int PAGES = 2;

    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> cache;

    public HimalayasSource(RestClient.Builder builder, Clock clock, PostingsProperties properties) {
        var apiKey = properties.himalayas().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.strip());
        }
        this.http = builder.baseUrl("https://himalayas.app").build();
        this.cache = new TtlCache<>(QUERY_TTL, clock, 50);
    }

    @Override public String id() { return "himalayas"; }
    @Override public String name() { return "Himalayas"; }
    @Override public String coverage() { return "Remote jobs with salary and location details."; }
    @Override public String attributionUrl() { return "https://himalayas.app"; }

    @Override
    public List<JobPosting> search(PostingQuery query) {
        var keywords = query.keywords().toLowerCase(Locale.ROOT);
        return cache.get(keywords, () -> fetch(keywords)).stream()
                .filter(p -> PostingMatcher.matches(p, query))
                .toList();
    }

    private List<JobPosting> fetch(String keywords) {
        var postings = new ArrayList<JobPosting>();
        for (int page = 1; page <= PAGES; page++) {
            var pageNumber = page;
            var body = http.get()
                    .uri(uri -> uri.path("/jobs/api/search").queryParam("q", keywords).queryParam("page", pageNumber).build())
                    .retrieve()
                    .body(JsonNode.class);
            var jobs = body.path("jobs");
            for (var job : jobs.values()) {
                postings.add(toPosting(job));
            }
            if (jobs.size() < 20) break;
        }
        return postings;
    }

    private JobPosting toPosting(JsonNode job) {
        var countries = Feeds.strings(job, "locationRestrictions");
        var tags = new ArrayList<>(Feeds.strings(job, "categories"));
        tags.addAll(Feeds.strings(job, "parentCategories"));
        tags.replaceAll(t -> t.replace('-', ' '));

        return new JobPosting(
                id(),
                Feeds.text(job, "guid"),
                Feeds.plain(Feeds.text(job, "title")),
                Feeds.text(job, "companyName"),
                // An empty restriction list means anyone, anywhere.
                countries.isEmpty() ? "Worldwide" : String.join(", ", countries),
                true,
                // Their terms require Apply to go through applicationLink.
                Feeds.text(job, "applicationLink"),
                epochSeconds(job, "pubDate"),
                // The latest date the listing stays up; jobs can close earlier.
                expiry(job),
                Feeds.positiveInt(job, "minSalary"),
                Feeds.positiveInt(job, "maxSalary"),
                Feeds.text(job, "currency"),
                period(Feeds.text(job, "salaryPeriod")),
                null,
                tags,
                Feeds.snippet(Feeds.text(job, "excerpt"))
        );
    }

    private static Instant epochSeconds(JsonNode job, String field) {
        var seconds = job.path(field).asLong(0);
        return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
    }

    private static LocalDate expiry(JsonNode job) {
        var instant = epochSeconds(job, "expiryDate");
        return instant == null ? null : LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String period(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "annual", "yearly", "year" -> "year";
            case "hourly", "hour" -> "hour";
            case "monthly", "month" -> "month";
            default -> value.toLowerCase(Locale.ROOT);
        };
    }
}
