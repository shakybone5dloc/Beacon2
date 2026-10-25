package dev.beacon.application.postings;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PostingSearchServiceTests {

    private static final PostingQuery QUERY = new PostingQuery("java", "", false);

    private record FakeSource(String id, boolean isConfigured, Function<PostingQuery, List<JobPosting>> results)
            implements JobSource {
        @Override public String name() { return id.toUpperCase(); }
        @Override public String coverage() { return "test"; }
        @Override public String attributionUrl() { return "https://" + id + ".example"; }
        @Override public String setupHint() { return "Set " + id + " key."; }
        @Override public List<JobPosting> search(PostingQuery query) { return results.apply(query); }
    }

    private static FakeSource returning(String id, JobPosting... postings) {
        return new FakeSource(id, true, q -> List.of(postings));
    }

    private static JobPosting posting(String source, String company, String title, String postedAt) {
        return new JobPosting(source, title, title, company, null, true, "https://" + source + ".example/" + title,
                postedAt == null ? null : Instant.parse(postedAt), null, null, null, null, null, null, List.of(), null, null);
    }

    @Test
    void mergesSourcesNewestFirst() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "Old", "2026-10-01T00:00:00Z")),
                returning("b", posting("b", "Globex", "New", "2026-10-20T00:00:00Z"),
                        posting("b", "Initech", "Undated", null))));

        var response = service.search(QUERY, Set.of());

        assertThat(response.postings()).extracting(p -> p.title()).containsExactly("New", "Old", "Undated");
        assertThat(response.sources()).extracting(s -> s.id() + ":" + s.count()).containsExactlyInAnyOrder("a:1", "b:2");
    }

    @Test
    void dropsTheSameAdFromASecondSource() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "Java Engineer", "2026-10-01T00:00:00Z")),
                returning("b", posting("b", "ACME", "Java  Engineer", "2026-10-02T00:00:00Z"))));

        assertThat(service.search(QUERY, Set.of()).postings()).hasSize(1);
    }

    @Test
    void searchesOnlyTheSelectedSources() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                returning("b", posting("b", "Globex", "B", null))));

        var response = service.search(QUERY, Set.of("b"));

        assertThat(response.postings()).extracting(p -> p.source()).containsOnly("b");
        assertThat(response.sources()).extracting(s -> s.id()).containsExactly("b");
    }

    @Test
    void emptySelectionSkipsUnconfiguredSources() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                new FakeSource("keyed", false, q -> { throw new AssertionError("should not be called"); })));

        assertThat(service.search(QUERY, Set.of()).sources()).extracting(s -> s.id()).containsExactly("a");
    }

    @Test
    void selectingAnUnconfiguredSourceExplainsWhatIsMissing() {
        var service = new PostingSearchService(List.of(
                new FakeSource("keyed", false, q -> { throw new AssertionError("should not be called"); })));

        var result = service.search(QUERY, Set.of("keyed")).sources().getFirst();

        assertThat(result.error()).contains("Needs an API key").contains("Set keyed key.");
    }

    @Test
    void aFailingSourceIsReportedWithoutLosingTheOthers() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                new FakeSource("broken", true, q -> { throw new IllegalStateException("boom"); })));

        var response = service.search(QUERY, Set.of());

        assertThat(response.postings()).hasSize(1);
        assertThat(response.sources()).filteredOn(s -> s.id().equals("broken"))
                .singleElement().satisfies(s -> assertThat(s.error()).isEqualTo("Could not reach BROKEN."));
    }

    @Test
    void aSlowSourceTimesOut() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                new FakeSource("slow", true, q -> {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                })), Set.of(), Duration.ofMillis(200));

        var response = service.search(QUERY, Set.of());

        assertThat(response.postings()).hasSize(1);
        assertThat(response.sources()).filteredOn(s -> s.id().equals("slow"))
                .singleElement().satisfies(s -> assertThat(s.error()).contains("took too long"));
    }

    @Test
    void aTurnedOffSourceIsListedButNeverSearched() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                new FakeSource("off", true, q -> { throw new AssertionError("should not be called"); })),
                Set.of(" OFF "), Duration.ofSeconds(5));

        assertThat(service.sources()).extracting(s -> s.id() + ":" + s.enabled() + ":" + s.configured())
                .containsExactly("a:true:true", "off:false:true");
        assertThat(service.search(QUERY, Set.of()).sources()).extracting(s -> s.id()).containsExactly("a");
        assertThat(service.search(QUERY, Set.of("off")).sources().getFirst().error()).isEqualTo("OFF is turned off.");
    }

    @Test
    void listsSourcesWithSetupHintsOnlyWhenUnconfigured() {
        var service = new PostingSearchService(List.of(
                returning("free"),
                new FakeSource("keyed", false, q -> List.of())));

        assertThat(service.sources()).extracting(s -> s.id() + ":" + s.configured() + ":" + s.setupHint())
                .containsExactly("free:true:null", "keyed:false:Set keyed key.");
    }
}
