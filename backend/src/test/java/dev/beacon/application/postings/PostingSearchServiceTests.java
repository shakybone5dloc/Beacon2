package dev.beacon.application.postings;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.infrastructure.security.KeyCipher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class PostingSearchServiceTests {

    private static final PostingQuery QUERY = new PostingQuery("java", "", false);
    private static final UUID USER = UUID.randomUUID();
    private static final Map<String, String> USAJOBS_KEY = Map.of("apiKey", "k-123", "email", "me@example.com");

    private record FakeSource(String id, BiFunction<PostingQuery, Map<String, String>, List<JobPosting>> results)
            implements JobSource {
        @Override public String name() { return id.toUpperCase(); }
        @Override public String coverage() { return "test"; }
        @Override public String attributionUrl() { return "https://" + id + ".example"; }
        @Override public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
            return results.apply(query, credentials);
        }
    }

    /** The user's saved keys by provider id; a null value stands for a key that can't be decrypted. */
    private record FakeCredentials(Map<String, Map<String, String>> saved) implements UserCredentials {
        @Override public Set<String> savedProviders(UUID userId) { return saved.keySet(); }
        @Override public Optional<Map<String, String>> credentials(UUID userId, String providerId) {
            if (!saved.containsKey(providerId)) return Optional.empty();
            var values = saved.get(providerId);
            if (values == null) throw new KeyCipher.UnreadableKeyException();
            return Optional.of(values);
        }
    }

    private static FakeSource returning(String id, JobPosting... postings) {
        return new FakeSource(id, (q, c) -> List.of(postings));
    }

    private static FakeSource neverCalled(String id) {
        return new FakeSource(id, (q, c) -> { throw new AssertionError(id + " should not be called"); });
    }

    private static JobPosting posting(String source, String company, String title, String postedAt) {
        return new JobPosting(source, title, title, company, null, true, "https://" + source + ".example/" + title,
                postedAt == null ? null : Instant.parse(postedAt), null, null, null, null, null, null, List.of(), null, null);
    }

    private static PostingSearchService service(List<JobSource> sources) {
        return service(sources, Map.of(), Set.of());
    }

    private static PostingSearchService service(List<JobSource> sources, Map<String, Map<String, String>> saved,
                                                Set<String> disabled) {
        return new PostingSearchService(sources, disabled, new ProviderCatalog(), new FakeCredentials(saved),
                Duration.ofSeconds(5));
    }

    @Test
    void mergesSourcesNewestFirst() {
        var service = service(List.of(
                returning("a", posting("a", "Acme", "Old", "2026-10-01T00:00:00Z")),
                returning("b", posting("b", "Globex", "New", "2026-10-20T00:00:00Z"),
                        posting("b", "Initech", "Undated", null))));

        var response = service.search(USER, QUERY, Set.of());

        assertThat(response.postings()).extracting(p -> p.title()).containsExactly("New", "Old", "Undated");
        assertThat(response.sources()).extracting(s -> s.id() + ":" + s.count()).containsExactlyInAnyOrder("a:1", "b:2");
    }

    @Test
    void dropsTheSameAdFromASecondSource() {
        var service = service(List.of(
                returning("a", posting("a", "Acme", "Java Engineer", "2026-10-01T00:00:00Z")),
                returning("b", posting("b", "ACME", "Java  Engineer", "2026-10-02T00:00:00Z"))));

        assertThat(service.search(USER, QUERY, Set.of()).postings()).hasSize(1);
    }

    @Test
    void searchesOnlyTheSelectedSources() {
        var service = service(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                returning("b", posting("b", "Globex", "B", null))));

        var response = service.search(USER, QUERY, Set.of("b"));

        assertThat(response.postings()).extracting(p -> p.source()).containsOnly("b");
        assertThat(response.sources()).extracting(s -> s.id()).containsExactly("b");
    }

    @Test
    void aKeyedSourceIsHiddenAndSkippedUntilTheUserAddsAKey() {
        var service = service(List.of(returning("a", posting("a", "Acme", "A", null)), neverCalled("usajobs")));

        assertThat(service.sources(USER)).extracting(s -> s.id()).containsExactly("a");
        assertThat(service.search(USER, QUERY, Set.of()).sources()).extracting(s -> s.id()).containsExactly("a");
        assertThat(service.search(USER, QUERY, Set.of("usajobs")).sources()).isEmpty();
    }

    @Test
    void aKeyedSourceIsSearchedWithTheUsersKey() {
        var seen = new ArrayList<Map<String, String>>();
        var service = service(List.of(new FakeSource("usajobs", (q, c) -> {
            seen.add(c);
            return List.of(posting("usajobs", "Agency", "Analyst", null));
        })), Map.of("usajobs", USAJOBS_KEY), Set.of());

        assertThat(service.sources(USER)).singleElement()
                .satisfies(s -> assertThat(s.usesYourKey()).isTrue());
        assertThat(service.search(USER, QUERY, Set.of()).postings()).hasSize(1);
        assertThat(seen).containsExactly(USAJOBS_KEY);
    }

    @Test
    void anOptionalKeyIsPassedWhenSavedAndTheSourceWorksWithoutOne() {
        var seen = new ArrayList<Map<String, String>>();
        var himalayas = new FakeSource("himalayas", (q, c) -> {
            seen.add(c);
            return List.of();
        });

        service(List.of(himalayas)).search(USER, QUERY, Set.of());
        service(List.of(himalayas), Map.of("himalayas", Map.of("apiKey", "h-1")), Set.of()).search(USER, QUERY, Set.of());

        assertThat(seen).containsExactly(Map.of(), Map.of("apiKey", "h-1"));
    }

    @Test
    void aTurnedOffSourceIsHiddenEvenWithAKey() {
        var service = service(List.of(returning("a"), neverCalled("usajobs")),
                Map.of("usajobs", USAJOBS_KEY), Set.of(" USAJOBS "));

        assertThat(service.sources(USER)).extracting(s -> s.id()).containsExactly("a");
        assertThat(service.search(USER, QUERY, Set.of("usajobs")).sources()).isEmpty();
    }

    @Test
    void aRejectedKeyIsReportedAsTheUsersToFix() {
        var service = service(List.of(new FakeSource("usajobs", (q, c) -> {
            throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        })), Map.of("usajobs", USAJOBS_KEY), Set.of());

        assertThat(service.search(USER, QUERY, Set.of()).sources().getFirst().error())
                .isEqualTo("USAJOBS rejected your API key. Check it under Settings.");
    }

    @Test
    void aKeyThatCantBeDecryptedIsReportedWithoutCallingTheSource() {
        var saved = new HashMap<String, Map<String, String>>();
        saved.put("usajobs", null);
        var service = service(List.of(neverCalled("usajobs")), saved, Set.of());

        assertThat(service.search(USER, QUERY, Set.of()).sources().getFirst().error()).contains("Add it again");
    }

    @Test
    void aFailingSourceIsReportedWithoutLosingTheOthers() {
        var service = service(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                new FakeSource("broken", (q, c) -> { throw new IllegalStateException("boom"); })));

        var response = service.search(USER, QUERY, Set.of());

        assertThat(response.postings()).hasSize(1);
        assertThat(response.sources()).filteredOn(s -> s.id().equals("broken"))
                .singleElement().satisfies(s -> assertThat(s.error()).isEqualTo("Could not reach BROKEN."));
    }

    @Test
    void aSlowSourceTimesOut() {
        var service = new PostingSearchService(List.of(
                returning("a", posting("a", "Acme", "A", null)),
                new FakeSource("slow", (q, c) -> {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return List.of();
                })), Set.of(), new ProviderCatalog(), new FakeCredentials(Map.of()), Duration.ofMillis(200));

        var response = service.search(USER, QUERY, Set.of());

        assertThat(response.postings()).hasSize(1);
        assertThat(response.sources()).filteredOn(s -> s.id().equals("slow"))
                .singleElement().satisfies(s -> assertThat(s.error()).contains("took too long"));
    }
}
