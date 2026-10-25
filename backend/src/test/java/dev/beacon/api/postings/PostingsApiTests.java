package dev.beacon.api.postings;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.application.postings.JobSource;
import dev.beacon.application.postings.PostingQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PostingsApiTests.FakeSourceConfig.class})
@Transactional
class PostingsApiTests extends SignedInApiTests {

    /** Searches go to this instead of the real job sites. */
    @TestConfiguration
    static class FakeSourceConfig {
        @Bean
        JobSource fakeSource() {
            return new JobSource() {
                @Override public String id() { return "fake"; }
                @Override public String name() { return "Fake"; }
                @Override public String coverage() { return "Test postings"; }
                @Override public String attributionUrl() { return "https://fake.example"; }

                @Override
                public List<JobPosting> search(PostingQuery query, Map<String, String> credentials) {
                    return List.of(new JobPosting("fake", "1", "Java " + query.keywords(), "Acme", query.location(),
                            query.remoteOnly(), "https://fake.example/1", Instant.parse("2026-10-20T00:00:00Z"),
                            LocalDate.parse("2026-11-15"), 100000, 120000, "USD", "year", null, List.of("spring"),
                            "Build things", "Build things with Java and Spring."));
                }
            };
        }
    }

    @Autowired ApiKeyService keys;

    @Test
    void listsOnlyTheSourcesThisUserCanSearch() {
        var result = mvc.get().uri("/api/postings/sources").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        // No USAJOBS (needs a key), no Adzuna (turned off by default with BEACON_DISABLED_SOURCES).
        assertThat(result).bodyJson().extractingPath("$[*].id").asArray()
                .containsExactlyInAnyOrder("fake", "himalayas", "jobicy", "remoteok", "remotive");
        assertThat(result).bodyJson().extractingPath("$[?(@.id == 'remotive')].attributionUrl").asArray()
                .containsExactly("https://remotive.com");
        assertThat(result).bodyJson().extractingPath("$[*].usesYourKey").asArray().containsOnly(false);
    }

    @Test
    void aSavedKeyAddsItsSourceForThatUserOnly() {
        keys.save(signedIn.id(), "usajobs", Map.of("apiKey", "usajobs-test-key", "email", "me@example.com"));

        assertThat(mvc.get().uri("/api/postings/sources").exchange())
                .bodyJson().extractingPath("$[?(@.id == 'usajobs')].usesYourKey").asArray().containsExactly(true);
        assertThat(as(register()).get().uri("/api/postings/sources").exchange())
                .bodyJson().extractingPath("$[*].id").asArray().doesNotContain("usajobs");
    }

    @Test
    void searchesTheSelectedSources() {
        var result = mvc.get().uri("/api/postings/search?keywords=engineer&location=Denver&remoteOnly=true&sources=fake")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.postings[0].title").isEqualTo("Java engineer");
        assertThat(result).bodyJson().extractingPath("$.postings[0].location").isEqualTo("Denver");
        assertThat(result).bodyJson().extractingPath("$.postings[0].remote").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.postings[0].closesOn").isEqualTo("2026-11-15");
        assertThat(result).bodyJson().extractingPath("$.sources[0].id").isEqualTo("fake");
        assertThat(result).bodyJson().extractingPath("$.sources[0].count").isEqualTo(1);
    }

    @Test
    void blankKeywordsReturn400() {
        var result = mvc.get().uri("/api/postings/search")
                .param("keywords", "  ")
                .param("sources", "fake")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.keywords").isNotNull();
    }

    @Test
    void unknownSourceReturns400() {
        var result = mvc.get().uri("/api/postings/search?keywords=java&sources=fake,linkedin").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.sources").isNotNull();
    }
}
