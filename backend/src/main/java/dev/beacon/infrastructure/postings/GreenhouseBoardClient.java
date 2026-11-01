package dev.beacon.infrastructure.postings;

import dev.beacon.application.postings.CareersBoardClient;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.domain.companies.CareersPlatform;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Open roles from a company's Greenhouse board (boards.greenhouse.io/{board}). No key needed. */
@Component
public class GreenhouseBoardClient implements CareersBoardClient {
    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> cache;   // one fetch per board per hour

    public GreenhouseBoardClient(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://boards-api.greenhouse.io").build();
        this.cache = new TtlCache<>(Duration.ofHours(1), clock, 200);
    }

    @Override public CareersPlatform platform() { return CareersPlatform.GREENHOUSE; }

    @Override
    public List<JobPosting> openings(String boardId, String companyName) {
        // The name is part of the key: users can name the same company differently.
        return cache.get(boardId + "|" + companyName, () -> fetch(boardId, companyName));
    }

    private List<JobPosting> fetch(String boardId, String companyName) {
        var body = http.get().uri("/v1/boards/{board}/jobs?content=true", boardId).retrieve().body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("jobs").values()) {
            var location = Feeds.text(job.path("location"), "name");
            var html = Feeds.plain(Feeds.text(job, "content"));   // Greenhouse escapes the HTML once
            var updated = Feeds.text(job, "updated_at");
            postings.add(new JobPosting(
                    "watchlist", "greenhouse:" + Feeds.text(job, "id"),
                    Feeds.text(job, "title"), companyName, location,
                    location != null && location.toLowerCase(Locale.ROOT).contains("remote"),
                    Feeds.text(job, "absolute_url"),
                    updated == null ? null : OffsetDateTime.parse(updated).toInstant(),
                    null, null, null, null, null, null, List.of(),
                    Feeds.snippet(html), Feeds.description(html)));
        }
        return postings;
    }
}