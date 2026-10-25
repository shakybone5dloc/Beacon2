package dev.beacon.api.applications;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApplicationPostingDetailsApiTests extends SignedInApiTests {

    private static final String WITH_DETAILS = """
            {"company": "Acme", "role": "Engineer", "location": " Denver, CO ", "remote": true,
             "salaryMin": 140000, "salaryMax": 170000, "salaryCurrency": "usd", "salaryPeriod": "year",
             "source": "Himalayas", "postedOn": "2026-10-20", "jobDescription": "  Build services.  "}
            """;

    @Test
    void createStoresPostingDetails() {
        var result = create(WITH_DETAILS);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.location").isEqualTo("Denver, CO");
        assertThat(result).bodyJson().extractingPath("$.remote").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.salaryMin").isEqualTo(140000);
        assertThat(result).bodyJson().extractingPath("$.salaryMax").isEqualTo(170000);
        assertThat(result).bodyJson().extractingPath("$.salaryCurrency").isEqualTo("USD");
        assertThat(result).bodyJson().extractingPath("$.salaryPeriod").isEqualTo("year");
        assertThat(result).bodyJson().extractingPath("$.source").isEqualTo("Himalayas");
        assertThat(result).bodyJson().extractingPath("$.postedOn").isEqualTo("2026-10-20");
        assertThat(result).bodyJson().extractingPath("$.jobDescription").isEqualTo("Build services.");
    }

    @Test
    void listLeavesOutTheDescriptionButGetIncludesIt() {
        var id = idOf(create(WITH_DETAILS));

        var list = mvc.get().uri("/api/applications").exchange();
        assertThat(list).bodyJson().extractingPath("$[0].jobDescription").isNull();
        assertThat(list).bodyJson().extractingPath("$[0].salaryMin").isEqualTo(140000);

        var one = mvc.get().uri("/api/applications/{id}", id).exchange();
        assertThat(one).bodyJson().extractingPath("$.jobDescription").isEqualTo("Build services.");
    }

    @Test
    void salaryRangeMustBeInOrder() {
        var result = create("""
                {"company": "Acme", "role": "Engineer", "salaryMin": 200000, "salaryMax": 100000}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.salaryMax").isNotNull();
    }

    @Test
    void currencyAndPeriodAreValidated() {
        var result = create("""
                {"company": "Acme", "role": "Engineer", "salaryCurrency": "dollars", "salaryPeriod": "fortnight"}
                """);

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors.salaryCurrency").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.errors.salaryPeriod").isNotNull();
    }

    @Test
    void appliedAtIsSetOnceTheApplicationMovesToApplied() {
        var id = idOf(create(WITH_DETAILS));
        assertThat(mvc.get().uri("/api/applications/{id}", id).exchange()).bodyJson().extractingPath("$.appliedAt").isNull();

        mvc.patch().uri("/api/applications/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"APPLIED\", \"version\": 0}")
                .exchange();

        assertThat(mvc.get().uri("/api/applications/{id}", id).exchange()).bodyJson().extractingPath("$.appliedAt").isNotNull();
    }

    @Test
    void updateReplacesPostingDetails() {
        var id = idOf(create(WITH_DETAILS));

        var result = mvc.put().uri("/api/applications/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"company": "Acme", "role": "Engineer", "location": "Remote", "salaryText": "DOE", "version": 0}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.location").isEqualTo("Remote");
        assertThat(result).bodyJson().extractingPath("$.salaryMin").isNull();
        assertThat(result).bodyJson().extractingPath("$.salaryText").isEqualTo("DOE");
        assertThat(result).bodyJson().extractingPath("$.jobDescription").isNull();
    }

    private MvcTestResult create(String body) {
        return mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String idOf(MvcTestResult created) {
        var location = created.getResponse().getHeader(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
