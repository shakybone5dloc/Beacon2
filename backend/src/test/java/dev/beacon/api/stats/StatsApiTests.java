package dev.beacon.api.stats;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Four applications with known histories: Acme offered, Globex rejected (both from
 * LinkedIn), Initech applied a month ago with no word (Himalayas), Hooli only saved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class StatsApiTests extends SignedInApiTests {

    @Autowired JdbcTemplate jdbc;

    /** Every history starts here, twenty days ago, so the times between changes are exact. */
    private final Instant start = Instant.now().minus(Duration.ofDays(20));
    private String hooli;

    @BeforeEach
    void history() {
        var acme = create("Acme", "LinkedIn");
        move(acme, "APPLIED", "INTERVIEWING", "OFFER");
        // Saved 1 day, waited 4 to hear back, interviewed for 10.
        times(acme, 0, 1, 5, 15);

        var globex = create("Globex", " LinkedIn ");
        move(globex, "APPLIED", "REJECTED");
        times(globex, 0, 1, 3);

        var initech = create("Initech", "Himalayas");
        move(initech, "APPLIED");
        times(initech, 0, 1);
        // Sent a month ago and nothing since: ghosted.
        jdbc.update("update application_status_changes set changed_at = changed_at - interval '11 days' "
                + "where application_id = ?::uuid", initech);
        jdbc.update("update job_applications set status_changed_at = now() - interval '30 days' where id = ?::uuid",
                initech);

        hooli = create("Hooli", null);
    }

    @Test
    void countsHowFarApplicationsGotFromTheirWholeHistory() {
        var stats = stats("");

        assertThat(stats).hasStatus(HttpStatus.OK);
        sameJson(stats, "$.overall", """
                {"name": null, "total": 4, "applied": 3, "responded": 2, "interviewed": 1, "offered": 1,
                 "ghosted": 1, "medianDaysToResponse": 3.0}
                """);
    }

    @Test
    void comparesSourcesBestFirstWithNoSourceLast() {
        var stats = stats("");

        assertThat(stats).bodyJson().extractingPath("$.bySource[*].name").asArray()
                .containsExactly("LinkedIn", "Himalayas", "Not recorded");
        sameJson(stats, "$.bySource[0]", """
                {"name": "LinkedIn", "total": 2, "applied": 2, "responded": 2, "interviewed": 1, "offered": 1,
                 "ghosted": 0, "medianDaysToResponse": 3.0}
                """);
        assertThat(stats).bodyJson().extractingPath("$.bySource[1].ghosted").isEqualTo(1);
        // No fit checks yet, so no resume is known for any of them.
        assertThat(stats).bodyJson().extractingPath("$.byResume[*].name").asArray().containsExactly("Not recorded");
        assertThat(stats).bodyJson().extractingPath("$.byResume[0].total").isEqualTo(4);
    }

    @Test
    void measuresHowLongEachStageTakes() {
        var stats = stats("");

        assertThat(stats).bodyJson().extractingPath("$.stages[*].stage").asArray()
                .containsExactly("SAVED", "APPLIED", "INTERVIEWING", "OFFER");
        sameJson(stats, "$.stages[0]", """
                {"stage": "SAVED", "moved": 3, "current": 1, "averageDays": 1.0, "medianDays": 1.0}
                """);
        // Acme waited 4 days, Globex 2; Initech is still waiting.
        sameJson(stats, "$.stages[1]", """
                {"stage": "APPLIED", "moved": 2, "current": 1, "averageDays": 3.0, "medianDays": 3.0}
                """);
        sameJson(stats, "$.stages[2]", """
                {"stage": "INTERVIEWING", "moved": 1, "current": 0, "averageDays": 10.0, "medianDays": 10.0}
                """);
        sameJson(stats, "$.stages[3]", """
                {"stage": "OFFER", "moved": 0, "current": 1, "averageDays": null, "medianDays": null}
                """);
    }

    @Test
    void countsWhatWentOutEachWeekForTwelveWeeks() {
        var stats = stats("?zone=America/Denver");

        List<String> weeks = read(stats, "$.weeks[*].weekStart");
        List<Integer> applied = read(stats, "$.weeks[*].applied");
        assertThat(weeks).hasSize(12);
        assertThat(LocalDate.parse(weeks.getLast()).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(applied.stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
    }

    @Test
    void canBeLimitedToRecentApplications() {
        jdbc.update("update job_applications set created_at = now() - interval '40 days' where id <> ?::uuid", hooli);

        var stats = stats("?days=30");

        assertThat(stats).bodyJson().extractingPath("$.overall.total").isEqualTo(1);
        assertThat(stats).bodyJson().extractingPath("$.since").isNotNull();
        assertThat(stats("?days=0")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(stats("?zone=Mars/Olympus")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anotherUsersApplicationsArentCounted() {
        var theirs = as(register()).get().uri("/api/stats").exchange();

        assertThat(theirs).bodyJson().extractingPath("$.overall.total").isEqualTo(0);
        assertThat(theirs).bodyJson().extractingPath("$.bySource").asArray().isEmpty();
    }

    private MvcTestResult stats(String query) {
        return mvc.get().uri("/api/stats" + query).exchange();
    }

    private String create(String company, String source) {
        var json = source == null
                ? "{\"companyName\": \"%s\", \"role\": \"Engineer\"}".formatted(company)
                : "{\"companyName\": \"%s\", \"role\": \"Engineer\", \"source\": \"%s\"}".formatted(company, source);
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return read(result, "$.id");
    }

    private void move(String id, String... statuses) {
        var version = 0;
        for (var status : statuses) {
            var result = mvc.patch().uri("/api/applications/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\": \"%s\", \"version\": %d}".formatted(status, version)).exchange();
            assertThat(result).hasStatus(HttpStatus.OK);
            version = read(result, "$.version");
        }
    }

    /** Sets each status change, in order, to {@code start} plus the given days. */
    private void times(String id, int... days) {
        List<Long> changes = jdbc.queryForList(
                "select id from application_status_changes where application_id = ?::uuid order by changed_at, id",
                Long.class, id);
        assertThat(changes).hasSize(days.length);
        for (int i = 0; i < days.length; i++) {
            jdbc.update("update application_status_changes set changed_at = ? where id = ?",
                    Timestamp.from(start.plus(Duration.ofDays(days[i]))), changes.get(i));
        }
        jdbc.update("update job_applications set status_changed_at = ?, created_at = ? where id = ?::uuid",
                Timestamp.from(start.plus(Duration.ofDays(days[days.length - 1]))), Timestamp.from(start), id);
    }

    /** The JSON at {@code path} equals {@code expected}, whatever order its fields are in. */
    private static void sameJson(MvcTestResult result, String path, String expected) {
        Object actual = read(result, path);
        assertThat(actual).isEqualTo(JsonPath.parse(expected).json());
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
