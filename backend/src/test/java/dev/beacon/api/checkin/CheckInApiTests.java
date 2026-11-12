package dev.beacon.api.checkin;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeCheckInWriter;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.apikeys.ApiKeyService;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class CheckInApiTests extends SignedInApiTests {

    @Autowired FakeCheckInWriter writer;
    @Autowired ApiKeyService keys;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        writer.reset();
    }

    @Test
    void startsWithTheTemplateThePeopleOnTheApplicationAndWhereEmailOpens() {
        var appId = application();
        String pat = read(post("/api/people", """
                {"name": "Pat Lee", "role": "RECRUITER", "email": "pat@elevance.example"}
                """), "$.id");
        assertThat(mvc.put().uri("/api/applications/{a}/people/{p}", appId, pat)
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(HttpStatus.OK);

        var start = mvc.get().uri("/api/applications/{id}/check-in?zone=America/Indianapolis", appId).exchange();

        assertThat(start).hasStatus(HttpStatus.OK);
        assertThat(start).bodyJson().extractingPath("$.recipients[0].name").isEqualTo("Pat Lee");
        assertThat(start).bodyJson().extractingPath("$.recipients[0].email").isEqualTo("pat@elevance.example");
        assertThat(start).bodyJson().extractingPath("$.defaultTeam").isEqualTo("Hiring team");
        assertThat(start).bodyJson().extractingPath("$.subject").isEqualTo("Checking in: Staff Engineer application");
        String body = read(start, "$.body");
        assertThat(body).startsWith("Hello,\n\n")
                .contains("the Staff Engineer position at Elevance Health")
                .contains("any update on my application");
        assertThat(start).bodyJson().extractingPath("$.mailClient").isEqualTo("GMAIL");
        assertThat(start).bodyJson().extractingPath("$.aiAvailable").isEqualTo(false);
    }

    @Test
    void theTemplateThanksForTheLastInterviewOnceThereHasBeenOne() {
        var appId = application();
        post("/api/applications/" + appId + "/interviews", """
                {"localStart": "2026-11-03T14:00:00", "timeZone": "America/Indianapolis", "durationMinutes": 45,
                 "type": "TECHNICAL"}
                """);

        var start = mvc.get().uri("/api/applications/{id}/check-in?zone=America/Indianapolis", appId).exchange();

        String body = read(start, "$.body");
        assertThat(body).contains("Thank you again for the interview on November 3").contains("next steps");
    }

    @Test
    void draftsWithTheUsersKeyFromTheApplicationsHistory() {
        keys.save(signedIn.id(), "openai", Map.of("apiKey", "sk-test-users-own"));
        var appId = application();
        post("/api/contacts", """
                {"team": "Hiring team", "applicationId": "%s", "channel": "EMAIL", "direction": "OUTBOUND",
                 "occurredAt": "2026-11-05T15:00:00Z", "body": "Checking in on my application."}
                """.formatted(appId));

        var draft = post("/api/applications/" + appId + "/check-in/draft?zone=UTC",
                "{\"recipient\": \"Pat Lee\", \"body\": \"Hello,\"}");

        assertThat(draft).hasStatus(HttpStatus.OK);
        assertThat(draft).bodyJson().extractingPath("$.subject").isEqualTo("Following up: Staff Engineer");
        assertThat(draft).bodyJson().extractingPath("$.body").isEqualTo("Hello Pat Lee,\n\nAny update?");
        var input = writer.inputs.getFirst();
        assertThat(input.company()).isEqualTo("Elevance Health");
        assertThat(input.recipient()).isEqualTo("Pat Lee");
        assertThat(input.starting()).isEqualTo("Hello,");
        assertThat(input.history()).containsExactly(
                "November 5: I wrote to Hiring team (email): Checking in on my application.");
        assertThat(jdbc.queryForObject("select count(*) from ai_usage where feature = 'CHECK_IN_EMAIL'", Integer.class))
                .isEqualTo(1);
        assertThat(mvc.get().uri("/api/applications/{id}/check-in", appId).exchange())
                .bodyJson().extractingPath("$.aiAvailable").isEqualTo(true);
    }

    @Test
    void draftingNeedsAKeyAndAnApplication() {
        var appId = application();

        var noKey = post("/api/applications/" + appId + "/check-in/draft", "{}");
        assertThat(noKey).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(noKey).bodyText().contains("Add an OpenAI or Anthropic API key");
        assertThat(writer.inputs).isEmpty();

        assertThat(mvc.get().uri("/api/applications/{id}/check-in", java.util.UUID.randomUUID()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void remembersWhereEmailOpens() {
        assertThat(mvc.get().uri("/api/me/mail-client").exchange())
                .bodyJson().extractingPath("$.mailClient").isEqualTo("GMAIL");

        var saved = mvc.put().uri("/api/me/mail-client").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mailClient\": \"MAIL_APP\"}").exchange();

        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/me/mail-client").exchange())
                .bodyJson().extractingPath("$.mailClient").isEqualTo("MAIL_APP");
        assertThat(mvc.put().uri("/api/me/mail-client").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mailClient\": \"PIGEON\"}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private String application() {
        return read(post("/api/applications", """
                {"companyName": "Elevance Health", "role": "Staff Engineer"}
                """), "$.id");
    }

    private MvcTestResult post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
