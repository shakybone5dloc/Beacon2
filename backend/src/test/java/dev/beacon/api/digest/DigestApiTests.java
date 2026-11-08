package dev.beacon.api.digest;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.DocumentTestDoubles.FakeMailer;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import dev.beacon.application.digest.DigestService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DocumentTestDoubles.class})
@Transactional
class DigestApiTests extends SignedInApiTests {

    @Autowired FakeMailer mailer;
    @Autowired DigestService digests;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    @BeforeEach
    void setUp() {
        mailer.reset();
        // The signed-in user is saved through JPA; plain SQL below needs it in the table.
        entities.flush();
    }

    @Test
    void startsOffOnMondayMorningsInTheBrowsersZoneAndSavesChanges() {
        var defaults = mvc.get().uri("/api/me/digest?zone=America/Chicago").exchange();
        assertThat(defaults).hasStatus(HttpStatus.OK);
        assertThat(defaults).bodyJson().extractingPath("$.enabled").isEqualTo(false);
        assertThat(defaults).bodyJson().extractingPath("$.dayOfWeek").isEqualTo("MONDAY");
        assertThat(defaults).bodyJson().extractingPath("$.hour").isEqualTo(8);
        assertThat(defaults).bodyJson().extractingPath("$.zone").isEqualTo("America/Chicago");
        assertThat(defaults).bodyJson().extractingPath("$.mailConfigured").isEqualTo(true);
        assertThat(defaults).bodyJson().extractingPath("$.nextSendAt").isNull();

        var saved = save("""
                {"enabled": true, "dayOfWeek": "FRIDAY", "hour": 17, "zone": "America/Chicago"}
                """);
        assertThat(saved).hasStatus(HttpStatus.OK);
        assertThat(saved).bodyJson().extractingPath("$.dayOfWeek").isEqualTo("FRIDAY");
        assertThat(saved).bodyJson().extractingPath("$.nextSendAt").asString().contains("T17:00");

        // Saving again needs the version it was loaded at.
        assertThat(save("""
                {"enabled": false, "dayOfWeek": "FRIDAY", "hour": 17, "zone": "America/Chicago"}
                """)).hasStatus(HttpStatus.CONFLICT);
        assertThat(save("""
                {"enabled": false, "dayOfWeek": "FRIDAY", "hour": 17, "zone": "America/Chicago", "version": 0}
                """)).bodyJson().extractingPath("$.enabled").isEqualTo(false);
    }

    @Test
    void rejectsAnUnknownDayZoneOrHour() {
        assertThat(save("{\"enabled\": true, \"dayOfWeek\": \"FUNDAY\", \"hour\": 8, \"zone\": \"UTC\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(save("{\"enabled\": true, \"dayOfWeek\": \"MONDAY\", \"hour\": 8, \"zone\": \"Mars/Base\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(save("{\"enabled\": true, \"dayOfWeek\": \"MONDAY\", \"hour\": 24, \"zone\": \"UTC\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void previewsHomesSectionsAsAnEmail() {
        jdbc.update("update users set display_name = 'Aaron Keiper' where id = ?", signedIn.id());
        entities.clear();
        var overdue = create("{\"companyName\": \"Elevance Health\", \"role\": \"Staff IAM Engineer\"}");
        var today = LocalDate.now(ZoneOffset.UTC);
        mvc.put().uri("/api/applications/{id}/follow-up", overdue).contentType(MediaType.APPLICATION_JSON)
                .content("{\"followUpOn\": \"" + today.minusDays(2) + "\", \"version\": 0}").exchange();
        create("{\"companyName\": \"<Script> & Co\", \"role\": \"Dev\"}");

        var preview = mvc.get().uri("/api/me/digest/preview?zone=UTC").exchange();

        assertThat(preview).hasStatus(HttpStatus.OK);
        assertThat(preview).bodyJson().extractingPath("$.subject").isEqualTo("Beacon: 1 needs attention");
        String html = read(preview, "$.html");
        String text = read(preview, "$.text");
        assertThat(html).contains("Hi Aaron, here&#39;s your week", "Needs attention", "Elevance Health",
                "Follow-up 2d overdue", "/applications/" + overdue, "This week", "/settings/digest");
        // Names are escaped, never markup.
        assertThat(html).doesNotContain("<Script>");
        assertThat(text).contains("NEEDS ATTENTION", "- Elevance Health · Staff IAM Engineer: Follow-up 2d overdue",
                "Saved 2");
        assertThat(mailer.sent).isEmpty();
    }

    @Test
    void sendsOneNowToTheUsersOwnAddress() {
        assertThat(mvc.post().uri("/api/me/digest/send?zone=UTC").exchange()).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mailer.sent).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo(email());
            assertThat(mail.subject()).isEqualTo("Beacon: your week — all caught up");
            assertThat(mail.text()).contains("You're all caught up");
        });
    }

    @Test
    void saysWhenThisServerCannotSendEmail() {
        mailer.configured = false;

        assertThat(mvc.get().uri("/api/me/digest").exchange())
                .bodyJson().extractingPath("$.mailConfigured").isEqualTo(false);
        assertThat(mvc.post().uri("/api/me/digest/send").exchange())
                .hasStatus(HttpStatus.valueOf(422))
                .bodyJson().extractingPath("$.detail").asString().contains("isn't set up");
        assertThat(digests.sendDue()).isZero();
    }

    @Test
    void sendsTheScheduledDigestOncePerWeek() {
        // Due today from midnight UTC, so it's due whenever the test runs.
        var day = Instant.now().atZone(ZoneOffset.UTC).getDayOfWeek();
        save("{\"enabled\": true, \"dayOfWeek\": \"" + day + "\", \"hour\": 0, \"zone\": \"UTC\"}");
        var other = register();
        as(other).put().uri("/api/me/digest").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": true, \"dayOfWeek\": \"" + day.plus(1) + "\", \"hour\": 0, \"zone\": \"UTC\"}")
                .exchange();

        assertThat(digests.sendDue()).isEqualTo(1);
        assertThat(mailer.sent).extracting(FakeMailer.Sent::to).containsExactly(email());
        assertThat(mvc.get().uri("/api/me/digest").exchange()).bodyJson().extractingPath("$.lastSentAt").isNotNull();

        assertThat(digests.sendDue()).as("already sent this week").isZero();
    }

    private MvcTestResult save(String json) {
        return mvc.put().uri("/api/me/digest").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private String create(String json) {
        return read(mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange(), "$.id");
    }

    private String email() {
        return jdbc.queryForObject("select email from users where id = ?", String.class, signedIn.id());
    }

    private static <T> T read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
