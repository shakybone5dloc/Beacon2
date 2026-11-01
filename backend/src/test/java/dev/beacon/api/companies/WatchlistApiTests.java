package dev.beacon.api.companies;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.postings.CareersBoardClient;
import dev.beacon.application.postings.CareersBoardClients;
import dev.beacon.application.postings.JobPosting;
import dev.beacon.domain.companies.CareersPlatform;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, WatchlistApiTests.FakeBoardsConfig.class})
@Transactional
class WatchlistApiTests extends SignedInApiTests {

    /** Every careers board is this fake Greenhouse: board "acme" has two roles, anything else fails. */
    @TestConfiguration
    static class FakeBoardsConfig {
        @Bean
        @Primary
        CareersBoardClients fakeBoards() {
            return new CareersBoardClients(List.of(new CareersBoardClient() {
                @Override public CareersPlatform platform() { return CareersPlatform.GREENHOUSE; }

                @Override
                public List<JobPosting> openings(String boardId, String companyName) {
                    if (!boardId.equals("acme")) throw new IllegalStateException("404 from the board");
                    return List.of(
                            posting("1", "Senior Java Engineer", companyName, "2026-10-20T00:00:00Z"),
                            posting("2", "Product Designer", companyName, "2026-10-25T00:00:00Z"));
                }
            }));
        }

        private static JobPosting posting(String id, String title, String company, String postedAt) {
            return new JobPosting("watchlist", "greenhouse:" + id, title, company, "Remote, US", true,
                    "https://boards.greenhouse.io/acme/jobs/" + id, Instant.parse(postedAt), null,
                    null, null, null, null, null, List.of(), "Build things",
                    title.contains("Java") ? "Spring Boot services on Kafka." : "Figma and research.");
        }
    }

    @Test
    void watchesACompanyFromItsCareersPageLink() {
        var companyId = company("Acme");

        var watched = watch(companyId, "https://boards.greenhouse.io/acme/jobs/123?gh_src=x");

        assertThat(watched).hasStatus(HttpStatus.OK);
        assertThat(watched).bodyJson().extractingPath("$.careersPlatform").isEqualTo("GREENHOUSE");
        assertThat(watched).bodyJson().extractingPath("$.careersBoardId").isEqualTo("acme");
        assertThat(mvc.get().uri("/api/companies/{id}", companyId).exchange())
                .bodyJson().extractingPath("$.careersBoardId").isEqualTo("acme");
    }

    @Test
    void listsTheOpeningsNewestFirst() {
        var companyId = company("Acme");
        assertThat(mvc.get().uri("/api/companies/{id}/openings", companyId).exchange())
                .bodyJson().extractingPath("$").asArray().isEmpty();

        watch(companyId, "https://boards.greenhouse.io/acme");

        var openings = mvc.get().uri("/api/companies/{id}/openings", companyId).exchange();
        assertThat(openings).hasStatus(HttpStatus.OK);
        assertThat(openings).bodyJson().extractingPath("$[*].title").asArray()
                .containsExactly("Product Designer", "Senior Java Engineer");
        assertThat(openings).bodyJson().extractingPath("$[0].company").isEqualTo("Acme");
    }

    /**
     * Searches run each source on its own thread, which can't see a test's uncommitted
     * rows, so tests that search commit their data instead of rolling it back.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void theWatchlistSearchesWatchedCompaniesOnly() {
        var acme = company("Acme");
        company("Globex");
        watch(acme, "https://boards.greenhouse.io/acme");

        var result = mvc.get().uri("/api/postings/search?keywords=java&sources=watchlist").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.postings[*].title").asArray()
                .containsExactly("Senior Java Engineer");
        assertThat(result).bodyJson().extractingPath("$.sources[0].count").isEqualTo(1);
        // Keywords also match the full description, which careers boards always include.
        assertThat(mvc.get().uri("/api/postings/search?keywords=kafka&sources=watchlist").exchange())
                .bodyJson().extractingPath("$.postings[*].title").asArray().containsExactly("Senior Java Engineer");
    }

    @Test
    void rejectsLinksItCantUse() {
        var companyId = company("Acme");

        var unknown = watch(companyId, "https://acme.example/careers");
        assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(unknown).bodyJson().extractingPath("$.errors.url[0]").asString().contains("Greenhouse, Lever or Ashby");

        // Looks like a Greenhouse board, but there's no such board.
        var missing = watch(companyId, "https://boards.greenhouse.io/no-such-company");
        assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(missing).bodyJson().extractingPath("$.errors.url[0]").asString().contains("Couldn't read");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void stopsWatching() {
        var companyId = company("Acme");
        watch(companyId, "https://boards.greenhouse.io/acme");
        assertThat(mvc.get().uri("/api/postings/search?keywords=java&sources=watchlist").exchange())
                .bodyJson().extractingPath("$.postings").asArray().hasSize(1);

        var stopped = mvc.delete().uri("/api/companies/{id}/careers-board", companyId).exchange();

        assertThat(stopped).hasStatus(HttpStatus.OK);
        assertThat(stopped).bodyJson().extractingPath("$.careersBoardId").isNull();
        assertThat(mvc.get().uri("/api/postings/search?keywords=java&sources=watchlist").exchange())
                .bodyJson().extractingPath("$.postings").asArray().isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void anotherUsersCompanyCantBeWatchedOrRead() {
        var companyId = company("Acme");
        watch(companyId, "https://boards.greenhouse.io/acme");
        var other = as(register());

        assertThat(other.put().uri("/api/companies/{id}/careers-board", companyId)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"url": "https://boards.greenhouse.io/acme"}
                        """).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/companies/{id}/careers-board", companyId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/companies/{id}/openings", companyId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        // Their watchlist doesn't include my companies.
        assertThat(other.get().uri("/api/postings/search?keywords=java&sources=watchlist").exchange())
                .bodyJson().extractingPath("$.postings").asArray().isEmpty();
    }

    private MvcTestResult watch(String companyId, String url) {
        return mvc.put().uri("/api/companies/{id}/careers-board", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"url": "%s"}
                        """.formatted(url))
                .exchange();
    }

    /** Companies are created by naming them on an application. */
    private String company(String name) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "%s", "role": "Engineer"}
                        """.formatted(name))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.companyId");
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
