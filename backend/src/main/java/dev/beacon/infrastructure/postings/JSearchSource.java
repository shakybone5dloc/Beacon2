package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.JobSourceException;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.config.PostingsConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JSearch reads Google's job listings and returns them already parsed: numeric pay, an
 * expiry date, the board each one came from. It is sold both by its maker, OpenWeb Ninja,
 * and on RapidAPI, with different keys and headers; a user's key is tried on OpenWeb Ninja
 * first and on RapidAPI if that rejects it (a rejected call costs nothing). Every page of
 * 10 results is one request against their monthly quota, so a search reads one page and
 * is cached for six hours.
 */
@Component
public class JSearchSource implements JobSource {

    static final String RAPIDAPI_HOST = "jsearch.p.rapidapi.com";
    private static final Duration QUERY_TTL = Duration.ofHours(6);

    private final RestClient openWebNinja;
    private final RestClient rapidApi;
    private final TtlCache<PostingQuery, List<JobPosting>> cache;

    public JSearchSource(@Qualifier(PostingsConfig.LIVE_SEARCH) RestClient.Builder builder, Clock clock) {
        this.openWebNinja = builder.clone().baseUrl("https://api.openwebninja.com/jsearch").build();
        this.rapidApi = builder.clone().baseUrl("https://" + RAPIDAPI_HOST).build();
        this.cache = new TtlCache<>(QUERY_TTL, clock, 50);
    }

    @Override public String id() { return ProviderCatalog.JSEARCH; }
    @Override public String name() { return "JSearch"; }
    @Override public String coverage() { return "Google's job listings, parsed: LinkedIn, Indeed, Glassdoor, ZipRecruiter and more."; }
    @Override public String attributionUrl() { return "https://www.openwebninja.com/api/jsearch"; }

    @Override
    public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
        return cache.get(query, () -> fetch(query, credentials.get("apiKey")));
    }

    private List<JobPosting> fetch(PostingQuery query, String apiKey) {
        try {
            return toPostings(call(query, apiKey));
        } catch (HttpClientErrorException ex) {
            // A rejected key keeps its own message; anything else (a used-up quota) JSearch explains.
            if (ex instanceof HttpClientErrorException.Unauthorized || ex instanceof HttpClientErrorException.Forbidden) throw ex;
            var message = Feeds.text(Feeds.errorBody(ex), "message");
            if (message == null) throw ex;
            throw new JobSourceException(message);
        }
    }

    private JsonNode call(PostingQuery query, String apiKey) {
        try {
            return Feeds.retryingDroppedConnections(() -> searchRequest(openWebNinja, query)
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden notTheirKey) {
            // Not an OpenWeb Ninja key; if RapidAPI rejects it too, the user is told to check it.
            return Feeds.retryingDroppedConnections(() -> searchRequest(rapidApi, query)
                    .header("X-RapidAPI-Key", apiKey)
                    .header("X-RapidAPI-Host", RAPIDAPI_HOST)
                    .retrieve()
                    .body(JsonNode.class));
        }
    }

    private List<JobPosting> toPostings(JsonNode body) {
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("data").values()) {
            postings.add(toPosting(job));
        }
        return postings;
    }

    /** The same search on either host. */
    private static RestClient.RequestHeadersSpec<?> searchRequest(RestClient http, PostingQuery query) {
        return http.get().uri(uri -> {
            uri.path("/search")
                    .queryParam("query", GoogleJobsSource.googleQuery(
                            new PostingQuery(query.keywords(), query.location(), false)))
                    .queryParam("page", 1)
                    .queryParam("num_pages", 1)
                    .queryParam("country", "us")
                    .queryParam("date_posted", "month");
            if (query.remoteOnly()) uri.queryParam("work_from_home", true);
            return uri.build();
        });
    }

    private JobPosting toPosting(JsonNode job) {
        var description = Feeds.text(job, "job_description");
        var tags = Stream.of(Feeds.text(job, "job_publisher"), employmentType(Feeds.text(job, "job_employment_type")))
                .filter(Objects::nonNull)
                .toList();
        var min = Feeds.positiveInt(job, "job_min_salary");
        var max = Feeds.positiveInt(job, "job_max_salary");
        var currency = Feeds.text(job, "job_salary_currency");
        if (currency == null && (min != null || max != null) && "US".equals(Feeds.text(job, "job_country"))) {
            currency = "USD";
        }

        return new JobPosting(
                id(),
                Feeds.text(job, "job_id"),
                Feeds.plain(Feeds.text(job, "job_title")),
                Feeds.text(job, "employer_name"),
                location(job),
                job.path("job_is_remote").asBoolean(false),
                Feeds.text(job, "job_apply_link"),
                epochSeconds(job, "job_posted_at_timestamp"),
                expiry(job),
                min,
                max,
                currency,
                period(Feeds.text(job, "job_salary_period")),
                null,
                tags,
                Feeds.snippet(description),
                Feeds.description(description)
        );
    }

    private static String location(JsonNode job) {
        var given = Feeds.text(job, "job_location");
        if (given != null) return given;
        var parts = Stream.of("job_city", "job_state", "job_country")
                .map(field -> Feeds.text(job, field))
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return parts.isEmpty() ? null : parts;
    }

    private static Instant epochSeconds(JsonNode job, String field) {
        var seconds = job.path(field).asLong(0);
        return seconds > 0 ? Instant.ofEpochSecond(seconds) : null;
    }

    private static LocalDate expiry(JsonNode job) {
        var instant = epochSeconds(job, "job_offer_expiration_timestamp");
        return instant == null ? null : LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String period(String value) {
        if (value == null) return null;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "YEAR", "YEARLY", "ANNUAL" -> "year";
            case "HOUR", "HOURLY" -> "hour";
            case "MONTH", "MONTHLY" -> "month";
            case "WEEK", "WEEKLY" -> "week";
            default -> value.toLowerCase(Locale.ROOT);
        };
    }

    /** "FULLTIME" reads better as "Full-time". */
    private static String employmentType(String value) {
        if (value == null) return null;
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "FULLTIME" -> "Full-time";
            case "PARTTIME" -> "Part-time";
            case "CONTRACTOR" -> "Contract";
            case "INTERN" -> "Internship";
            default -> value;
        };
    }
}
