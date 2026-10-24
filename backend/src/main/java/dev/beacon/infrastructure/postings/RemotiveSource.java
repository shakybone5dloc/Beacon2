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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Remote jobs from remotive.com. Their terms ask for at most ~4 calls a day,
 * so the whole feed is cached for six hours and searched locally.
 */
@Component
public class RemotiveSource implements JobSource {

    private static final Duration FEED_TTL = Duration.ofHours(6);

    private final RestClient http;
    private final TtlCache<String, List<JobPosting>> feed;

    public RemotiveSource(RestClient.Builder builder, Clock clock) {
        this.http = builder.baseUrl("https://remotive.com").build();
        this.feed = new TtlCache<>(FEED_TTL, clock, 1);
    }

    @Override public String id() { return "remotive"; }
    @Override public String name() { return "Remotive"; }
    @Override public String coverage() { return "Remote jobs, mostly tech. Listings appear 24 hours after posting."; }
    @Override public String attributionUrl() { return "https://remotive.com"; }

    @Override
    public List<JobPosting> search(PostingQuery query) {
        return feed.get("all", this::fetchAll).stream()
                .filter(p -> PostingMatcher.matches(p, query))
                .toList();
    }

    private List<JobPosting> fetchAll() {
        var body = http.get().uri("/api/remote-jobs").retrieve().body(JsonNode.class);
        var postings = new ArrayList<JobPosting>();
        for (var job : body.path("jobs").values()) {
            var tags = new ArrayList<>(Feeds.strings(job, "tags"));
            var category = Feeds.text(job, "category");
            if (category != null) tags.add(category);

            var published = Feeds.text(job, "publication_date");
            postings.add(new JobPosting(
                    id(),
                    Feeds.text(job, "id"),
                    Feeds.plain(Feeds.text(job, "title")),
                    Feeds.text(job, "company_name"),
                    Feeds.text(job, "candidate_required_location"),
                    true,
                    Feeds.text(job, "url"),
                    // Published times come without a zone; Remotive serves them in UTC.
                    published == null ? null : LocalDateTime.parse(published).toInstant(ZoneOffset.UTC),
                    null,
                    null, null, null, null,
                    Feeds.text(job, "salary"),
                    tags,
                    Feeds.snippet(Feeds.text(job, "description")),
                    Feeds.description(Feeds.text(job, "description"))
            ));
        }
        return postings;
    }
}
