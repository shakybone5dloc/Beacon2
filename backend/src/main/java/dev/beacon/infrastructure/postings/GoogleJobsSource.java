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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Google's job listings through SerpApi, which reach LinkedIn, Indeed, ZipRecruiter and
 * company careers sites that have no API of their own. Each user brings their own SerpApi
 * key, and every call uses one of their monthly searches, so a search reads one page
 * (Google's 10 most relevant results) and is cached for six hours.
 */
@Component
public class GoogleJobsSource implements JobSource {

    private static final Duration QUERY_TTL = Duration.ofHours(6);
    /** SerpApi answers 200 with this message when Google simply found nothing. */
    private static final String NO_RESULTS = "hasn't returned any results";
    private static final Pattern AGO = Pattern.compile("(\\d+)\\+?\\s+(minute|hour|day|week|month)s?\\s+ago");
    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*([kK])?");

    private final RestClient http;
    private final Clock clock;
    private final TtlCache<PostingQuery, List<JobPosting>> cache;

    public GoogleJobsSource(@Qualifier(PostingsConfig.LIVE_SEARCH) RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://serpapi.com").build();
        this.clock = clock;
        this.cache = new TtlCache<>(QUERY_TTL, clock, 50);
    }

    @Override public String id() { return ProviderCatalog.GOOGLE_JOBS; }
    @Override public String name() { return "Google Jobs"; }
    @Override public String coverage() { return "Google's job listings: LinkedIn, Indeed, ZipRecruiter and company careers sites."; }
    @Override public String attributionUrl() { return "https://serpapi.com"; }

    @Override
    public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
        return cache.get(query, () -> fetch(query, credentials.get("apiKey")));
    }

    private List<JobPosting> fetch(PostingQuery query, String apiKey) {
        JsonNode body;
        try {
            body = Feeds.retryingDroppedConnections(() -> http.get()
                    .uri(uri -> uri.path("/search.json")
                            .queryParam("engine", "google_jobs")
                            .queryParam("q", googleQuery(query))
                            .queryParam("gl", "us")
                            .queryParam("hl", "en")
                            .queryParam("api_key", apiKey)
                            .build())
                    .retrieve()
                    .body(JsonNode.class));
        } catch (HttpClientErrorException ex) {
            // A rejected key keeps its own message; anything else SerpApi explains in "error".
            if (ex instanceof HttpClientErrorException.Unauthorized || ex instanceof HttpClientErrorException.Forbidden) throw ex;
            body = Feeds.errorBody(ex);
            if (Feeds.text(body, "error") == null) throw ex;
        }

        var error = Feeds.text(body, "error");
        if (error != null && !body.has("jobs_results")) {
            if (error.contains(NO_RESULTS)) return List.of();
            throw new JobSourceException(error);
        }

        var now = clock.instant();
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("jobs_results").values()) {
            postings.add(toPosting(job, now));
        }
        return postings;
    }

    /**
     * Google's own remote filter and its location parameter are both unreliable through the
     * API (the first is deprecated, the second only takes Google's place names), so both go
     * into the query as words, the way someone would type them into Google.
     */
    static String googleQuery(PostingQuery query) {
        var q = new StringBuilder(query.keywords());
        if (query.remoteOnly()) q.append(" remote");
        if (!query.location().isBlank()) q.append(" in ").append(query.location());
        return q.toString().strip();
    }

    private JobPosting toPosting(JsonNode job, Instant now) {
        var extensions = job.path("detected_extensions");
        var location = Feeds.text(job, "location");
        var description = Feeds.text(job, "description");
        var salary = Salary.parse(Feeds.text(extensions, "salary"));
        var via = Feeds.text(job, "via");
        var tags = new ArrayList<String>();
        if (via != null) tags.add(via.replaceFirst("(?i)^via\\s+", ""));
        var schedule = Feeds.text(extensions, "schedule_type");
        if (schedule != null) tags.add(schedule);

        return new JobPosting(
                id(),
                Feeds.text(job, "job_id"),
                Feeds.plain(Feeds.text(job, "title")),
                Feeds.text(job, "company_name"),
                location,
                extensions.path("work_from_home").asBoolean(false) || isRemote(location),
                applyLink(job),
                postedAt(Feeds.text(extensions, "posted_at"), now),
                null,
                salary.min(),
                salary.max(),
                salary.currency(),
                salary.period(),
                salary.text(),
                tags,
                Feeds.snippet(description),
                Feeds.description(description)
        );
    }

    /** The first apply option goes straight to the board or employer; the share link opens Google. */
    private static String applyLink(JsonNode job) {
        var first = Feeds.text(job.path("apply_options").path(0), "link");
        return first != null ? first : Feeds.text(job, "share_link");
    }

    private static boolean isRemote(String location) {
        if (location == null) return false;
        var where = location.toLowerCase(Locale.ROOT);
        return where.contains("anywhere") || where.contains("remote");
    }

    /** Google gives relative times only ("3 days ago", "30+ days ago"); this turns them back into instants. */
    static Instant postedAt(String text, Instant now) {
        if (text == null) return null;
        var m = AGO.matcher(text.toLowerCase(Locale.ROOT));
        if (!m.find()) return null;
        var amount = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "minute" -> now.minus(amount, ChronoUnit.MINUTES);
            case "hour" -> now.minus(amount, ChronoUnit.HOURS);
            case "day" -> now.minus(amount, ChronoUnit.DAYS);
            case "week" -> now.minus(amount * 7, ChronoUnit.DAYS);
            default -> now.minus(amount * 30, ChronoUnit.DAYS);
        };
    }

    /**
     * Google's salary line, such as "$120K–$150K a year" or "$25 an hour". Numbers come out
     * when it is in dollars with a clear period; anything else stays as text.
     */
    record Salary(Integer min, Integer max, String currency, String period, String text) {

        private static final Salary NONE = new Salary(null, null, null, null, null);

        static Salary parse(String text) {
            if (text == null) return NONE;
            var lower = text.toLowerCase(Locale.ROOT);
            var period = lower.contains("year") ? "year"
                    : lower.contains("hour") ? "hour"
                    : lower.contains("month") ? "month"
                    : lower.contains("week") ? "week"
                    : null;
            if (!text.contains("$") || period == null) return new Salary(null, null, null, null, text);

            var amounts = new ArrayList<Integer>();
            var m = AMOUNT.matcher(text);
            while (m.find() && amounts.size() < 2) {
                var number = Double.parseDouble(m.group(1).replace(",", ""));
                if (m.group(2) != null) number *= 1000;
                if (number > 0) amounts.add((int) Math.round(number));
            }
            if (amounts.isEmpty()) return new Salary(null, null, null, null, text);
            return new Salary(amounts.getFirst(), amounts.getLast(), "USD", period, null);
        }
    }
}
