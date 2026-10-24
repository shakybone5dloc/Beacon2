package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.config.PostingsProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adzuna aggregates ads from many job boards and employer sites, including
 * on-site roles. Needs a free app id and key; the free tier allows a few
 * hundred calls a day, so each query is cached for an hour.
 */
@Component
public class AdzunaSource implements JobSource {

    private static final Duration QUERY_TTL = Duration.ofHours(1);
    private static final int RESULTS = 50;
    private static final Map<String, String> CURRENCIES = Map.of(
            "us", "USD", "gb", "GBP", "ca", "CAD", "au", "AUD", "nz", "NZD",
            "in", "INR", "sg", "SGD", "za", "ZAR", "br", "BRL", "pl", "PLN");

    private final RestClient http;
    private final PostingsProperties.Adzuna config;
    private final TtlCache<PostingQuery, List<JobPosting>> cache;

    public AdzunaSource(RestClient.Builder builder, Clock clock, PostingsProperties properties) {
        this.http = builder.baseUrl("https://api.adzuna.com").build();
        this.config = properties.adzuna();
        this.cache = new TtlCache<>(QUERY_TTL, clock, 50);
    }

    @Override public String id() { return "adzuna"; }
    @Override public String name() { return "Adzuna"; }
    @Override public String coverage() { return "Aggregated ads from many boards and employers, on-site and remote."; }
    @Override public String attributionUrl() { return "https://www.adzuna.com"; }
    @Override public boolean isConfigured() { return config.configured(); }

    @Override
    public String setupHint() {
        return "Get a free key at developer.adzuna.com, then set ADZUNA_APP_ID and ADZUNA_APP_KEY in .env.";
    }

    @Override
    public List<JobPosting> search(PostingQuery query) {
        return cache.get(query, () -> fetch(query));
    }

    private List<JobPosting> fetch(PostingQuery query) {
        // Adzuna has no remote filter; asking for the word is the closest match.
        var what = query.remoteOnly() ? query.keywords() + " remote" : query.keywords();

        var body = http.get()
                .uri(uri -> {
                    uri.path("/v1/api/jobs/{country}/search/1")
                            .queryParam("app_id", config.appId())
                            .queryParam("app_key", config.appKey())
                            .queryParam("results_per_page", RESULTS)
                            .queryParam("what", what)
                            .queryParam("content-type", "application/json");
                    if (!query.location().isBlank()) uri.queryParam("where", query.location());
                    return uri.build(config.country());
                })
                .retrieve()
                .body(JsonNode.class);

        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("results").values()) {
            var title = Feeds.plain(Feeds.text(job, "title"));
            var snippet = Feeds.snippet(Feeds.text(job, "description"));
            var created = Feeds.text(job, "created");
            // Predicted salaries are Adzuna estimates, not what the ad says.
            var predicted = "1".equals(Feeds.text(job, "salary_is_predicted"));
            var category = Feeds.text(job.path("category"), "label");

            postings.add(new JobPosting(
                    id(),
                    Feeds.text(job, "id"),
                    title,
                    Feeds.text(job.path("company"), "display_name"),
                    Feeds.text(job.path("location"), "display_name"),
                    mentionsRemote(title) || mentionsRemote(snippet),
                    Feeds.text(job, "redirect_url"),
                    created == null ? null : OffsetDateTime.parse(created).toInstant(),
                    null,
                    predicted ? null : Feeds.positiveInt(job, "salary_min"),
                    predicted ? null : Feeds.positiveInt(job, "salary_max"),
                    CURRENCIES.getOrDefault(config.country(), "EUR"),
                    "year",
                    null,
                    category == null ? List.of() : List.of(category),
                    snippet
            ));
        }
        return postings;
    }

    private static boolean mentionsRemote(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("remote");
    }
}
