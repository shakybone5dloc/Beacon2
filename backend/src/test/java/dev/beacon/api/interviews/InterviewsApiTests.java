package dev.beacon.api.interviews;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class InterviewsApiTests extends SignedInApiTests {

    @Test
    void schedulesAnInterviewInItsOwnTimeZoneAndPutsTheInterviewersOnTheApplication() {
        var appId = id(createApplication("Acme", "Java Engineer"));
        var sam = id(createPerson("Sam Ortiz", "INTERVIEWER"));

        var result = schedule(appId, """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "America/Chicago", "durationMinutes": 45,
                 "type": "TECHNICAL", "location": "https://meet.example/abc", "interviewerIds": ["%s"],
                 "prepNotes": "  Review the payments design.  "}
                """.formatted(sam));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().containsHeader(HttpHeaders.LOCATION);
        // 2pm Central in March 2099 is daylight time, UTC-5.
        assertThat(result).bodyJson().extractingPath("$.startsAt").isEqualTo("2099-03-10T19:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.endsAt").isEqualTo("2099-03-10T19:45:00Z");
        assertThat(result).bodyJson().extractingPath("$.localStart").isEqualTo("2099-03-10T14:00:00");
        assertThat(result).bodyJson().extractingPath("$.interviewers[*].name").asArray().containsExactly("Sam Ortiz");
        assertThat(result).bodyJson().extractingPath("$.prepNotes").isEqualTo("Review the payments design.");
        assertThat(result).bodyJson().extractingPath("$.outcome").isNull();

        var onApp = mvc.get().uri("/api/applications/{id}/people", appId).exchange();
        assertThat(onApp).bodyJson().extractingPath("$[*].person.name").asArray().containsExactly("Sam Ortiz");
        assertThat(onApp).bodyJson().extractingPath("$[*].role").asArray().containsExactly("INTERVIEWER");
    }

    @Test
    void listsAnApplicationsInterviewsEarliestFirst() {
        var appId = id(createApplication("Acme", "Engineer"));
        schedule(appId, interview("2099-05-01T10:00:00", "ONSITE"));
        schedule(appId, interview("2099-04-01T10:00:00", "PHONE_SCREEN"));

        assertThat(mvc.get().uri("/api/applications/{id}/interviews", appId).exchange())
                .bodyJson().extractingPath("$[*].type").asArray().containsExactly("PHONE_SCREEN", "ONSITE");
    }

    @Test
    void theBoardKnowsTheNextAndLastInterviewLeavingOutCancelledOnes() {
        var appId = id(createApplication("Acme", "Engineer"));
        var other = id(createApplication("Globex", "Engineer"));
        schedule(appId, interview("2020-01-15T10:00:00", "PHONE_SCREEN"));
        schedule(appId, interview("2099-02-01T10:00:00", "TECHNICAL"));
        schedule(appId, interview("2099-01-01T10:00:00", "PANEL", "CANCELLED"));

        var board = mvc.get().uri("/api/applications").exchange();
        assertThat(board).bodyJson().extractingPath("$[?(@.company == 'Acme')].nextInterviewAt").asArray()
                .containsExactly("2099-02-01T10:00:00Z");
        assertThat(board).bodyJson().extractingPath("$[?(@.company == 'Acme')].lastInterviewAt").asArray()
                .containsExactly("2020-01-15T10:00:00Z");
        assertThat(board).bodyJson().extractingPath("$[?(@.company == 'Globex')].nextInterviewAt").asArray()
                .containsExactly((Object) null);

        assertThat(mvc.get().uri("/api/applications/{id}", appId).exchange())
                .bodyJson().extractingPath("$.nextInterviewAt").isEqualTo("2099-02-01T10:00:00Z");
        assertThat(mvc.get().uri("/api/applications/{id}", other).exchange())
                .bodyJson().extractingPath("$.lastInterviewAt").isNull();
    }

    @Test
    void rejectsAnUnknownTimeZoneAndMissingFields() {
        var appId = id(createApplication("Acme", "Engineer"));

        var badZone = schedule(appId, """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "Mars/Olympus", "durationMinutes": 30,
                 "type": "OTHER"}
                """);
        assertThat(badZone).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(badZone).bodyJson().extractingPath("$.errors.timeZone").isNotNull();

        var missing = schedule(appId, """
                {"timeZone": "UTC", "durationMinutes": 0}
                """);
        assertThat(missing).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(missing).bodyJson().extractingPath("$.errors.localStart").isNotNull();
        assertThat(missing).bodyJson().extractingPath("$.errors.type").isNotNull();
        assertThat(missing).bodyJson().extractingPath("$.errors.durationMinutes").isNotNull();
    }

    @Test
    void updatesWithTheVersionItWasLoadedAtAndDeletes() {
        var appId = id(createApplication("Acme", "Engineer"));
        var interviewId = id(schedule(appId, interview("2099-03-10T14:00:00", "PHONE_SCREEN")));

        var updated = update(interviewId, """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 30,
                 "type": "PHONE_SCREEN", "debriefNotes": "Went well", "outcome": "PASSED", "version": 0}
                """);
        assertThat(updated).hasStatus(HttpStatus.OK);
        assertThat(updated).bodyJson().extractingPath("$.outcome").isEqualTo("PASSED");
        assertThat(updated).bodyJson().extractingPath("$.debriefNotes").isEqualTo("Went well");

        assertThat(update(interviewId, """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 30,
                 "type": "PHONE_SCREEN", "version": 0}
                """)).hasStatus(HttpStatus.CONFLICT);

        assertThat(mvc.delete().uri("/api/interviews/{id}", interviewId).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.delete().uri("/api/interviews/{id}", interviewId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadsAnInterviewAsACalendarFile() {
        var appId = id(createApplication("Acme, Inc.", "Java Engineer"));
        var interviewId = id(schedule(appId, """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "America/Chicago", "durationMinutes": 60,
                 "type": "PHONE_SCREEN", "location": "https://meet.example/abc"}
                """));

        var ics = mvc.get().uri("/api/interviews/{id}/calendar.ics", interviewId).exchange();

        assertThat(ics).hasStatus(HttpStatus.OK);
        assertThat(ics).hasContentTypeCompatibleWith("text/calendar");
        assertThat(ics).headers().hasValue(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"interview-acme-inc-2099-03-10.ics\"");
        assertThat(ics).bodyText()
                .contains("DTSTART:20990310T190000Z\r\n")
                .contains("DTEND:20990310T200000Z\r\n")
                .contains("SUMMARY:Phone screen: Acme\\, Inc. – Java Engineer\r\n")
                .contains("URL:https://meet.example/abc\r\n")
                .contains("UID:" + interviewId + "@beacon\r\n");
    }

    @Test
    void deletingAPersonTakesThemOffInterviewsAndDeletingTheApplicationRemovesThem() {
        var appId = id(createApplication("Acme", "Engineer"));
        var sam = id(createPerson("Sam", "INTERVIEWER"));
        var interviewId = id(schedule(appId, """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 30,
                 "type": "PANEL", "interviewerIds": ["%s"]}
                """.formatted(sam)));
        mvc.get().uri("/api/applications/{id}/interviews", appId).exchange();

        assertThat(mvc.delete().uri("/api/people/{id}", sam).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/applications/{id}/interviews", appId).exchange())
                .bodyJson().extractingPath("$[0].interviewers").asArray().isEmpty();

        assertThat(mvc.delete().uri("/api/applications/{id}", appId).exchange()).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/interviews/{id}/calendar.ics", interviewId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherUsersInterviewsAndPeopleCantBeReached() {
        var appId = id(createApplication("Acme", "Engineer"));
        var sam = id(createPerson("Sam", "INTERVIEWER"));
        var interviewId = id(schedule(appId, interview("2099-03-10T14:00:00", "OTHER")));
        var other = as(register());
        var theirApp = other.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "Globex", "role": "Engineer"}
                        """).exchange();

        assertThat(other.get().uri("/api/applications/{id}/interviews", appId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.post().uri("/api/applications/{id}/interviews", appId).contentType(MediaType.APPLICATION_JSON)
                .content(interview("2099-03-10T14:00:00", "OTHER")).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.get().uri("/api/interviews/{id}/calendar.ics", interviewId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other.delete().uri("/api/interviews/{id}", interviewId).exchange()).hasStatus(HttpStatus.NOT_FOUND);

        var stolenPerson = other.post().uri("/api/applications/{id}/interviews", id(theirApp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 30,
                         "type": "OTHER", "interviewerIds": ["%s"]}
                        """.formatted(sam))
                .exchange();
        assertThat(stolenPerson).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(stolenPerson).bodyJson().extractingPath("$.errors.interviewerIds").isNotNull();
    }

    private static String interview(String localStart, String type) {
        return """
                {"localStart": "%s", "timeZone": "UTC", "durationMinutes": 60, "type": "%s"}
                """.formatted(localStart, type);
    }

    private static String interview(String localStart, String type, String outcome) {
        return """
                {"localStart": "%s", "timeZone": "UTC", "durationMinutes": 60, "type": "%s", "outcome": "%s"}
                """.formatted(localStart, type, outcome);
    }

    private MvcTestResult schedule(String applicationId, String json) {
        return mvc.post().uri("/api/applications/{id}/interviews", applicationId)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult update(String id, String json) {
        return mvc.put().uri("/api/interviews/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult createApplication(String company, String role) {
        var result = mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"companyName": "%s", "role": "%s"}
                        """.formatted(company, role)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private MvcTestResult createPerson(String name, String role) {
        var result = mvc.post().uri("/api/people").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "role": "%s"}
                        """.formatted(name, role)).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private static String id(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
