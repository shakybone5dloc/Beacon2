package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.CareersBoardClient;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.domain.companies.CareersPlatform;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Open roles from a company's Lever board (jobs.lever.co/{company}). No key needed. */
@Component
public class LeverBoardClient implements CareersBoardClient {
    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> cache;

    public LeverBoardClient(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://api.lever.co").build();
        this.cache = new TtlCache<>(Duration.ofHours(1), clock, 200);
    }

    @Override public CareersPlatform platform() { return CareersPlatform.LEVER; }

    @Override
    public List<JobPosting> openings(String boardId, String companyName) {
        // The name is part of the key: users can name the same company differently.
        return cache.get(boardId + "|" + companyName, () -> fetch(boardId, companyName));
    }

    private List<JobPosting> fetch(String boardId, String companyName) {
        var body = http.get().uri("/v0/postings/{company}?mode=json", boardId).retrieve().body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.values()) {
            var categories = job.path("categories");
            var location = Feeds.text(categories, "location");
            var workplace = Feeds.text(job, "workplaceType");
            var remote = "remote".equalsIgnoreCase(workplace)
                    || (location != null &&
                    location.toLowerCase(Locale.ROOT).contains("remote"));
            var description = Feeds.text(job, "descriptionPlain");
            var salary = job.path("salaryRange");

            var tags = new ArrayList<String>();
            for (var field : List.of("commitment", "team", "department")) {
                var value = Feeds.text(categories, field);
                if (value != null) tags.add(value);
            }

            postings.add(new JobPosting(
                    "watchlist",
                    "lever:" + Feeds.text(job, "id"),
                    Feeds.text(job, "text"),
                    companyName,
                    location,
                    remote,
                    Feeds.text(job, "hostedUrl"),
                    job.path("createdAt").isNumber() ?
                            Instant.ofEpochMilli(job.path("createdAt").asLong()) : null,
                    null,
                    number(salary.path("min")),
                    number(salary.path("max")),
                    Feeds.text(salary, "currency"),
                    period(Feeds.text(salary, "interval")),
                    null,
                    tags,
                    Feeds.snippet(description),
                    Feeds.description(description)));
        }
        return postings;
    }

    private static String period(String interval) {
        if (interval == null) return null;
        if (interval.contains("year")) return "year";
        if (interval.contains("month")) return "month";
        if (interval.contains("hour")) return "hour";
        return null;
    }

    private static Integer number(JsonNode node) {
        return node.isNumber() ? (int) Math.round(node.asDouble()) : null;
    }
}
