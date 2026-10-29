package dev.beacon.api.applications;

import com.jayway.jsonpath.JsonPath;
import dev.beacon.DocumentTestDoubles;
import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TimelineApiTests extends SignedInApiTests {

    @Test
    void mergesEverySourceNewestFirst() {
        var appId = read(json(mvc.post().uri("/api/applications"), """
                {"companyName": "Acme", "role": "Java Engineer"}
                """), "$.id");
        var pat = read(json(mvc.post().uri("/api/people"), """
                {"name": "Pat Lee", "role": "RECRUITER"}
                """), "$.id");
        // A contact dated in the past and an interview far ahead bracket everything else.
        json(mvc.post().uri("/api/contacts"), """
                {"personId": "%s", "applicationId": "%s", "channel": "LINKEDIN", "direction": "INBOUND",
                 "occurredAt": "2020-01-01T09:00:00Z", "body": "Interested in a Java role?"}
                """.formatted(pat, appId));
        json(mvc.post().uri("/api/applications/{id}/interviews", appId), """
                {"localStart": "2099-03-10T14:00:00", "timeZone": "UTC", "durationMinutes": 60, "type": "TECHNICAL"}
                """);
        var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", DocumentTestDoubles.pdfWithText("Resume"));
        assertThat(mvc.post().uri("/api/applications/{id}/documents", appId).multipart().file(file)
                .param("kind", "resume").exchange()).hasStatus(HttpStatus.CREATED);

        var timeline = mvc.get().uri("/api/applications/{id}/timeline", appId).exchange();

        assertThat(timeline).hasStatus(HttpStatus.OK);
        assertThat(timeline).bodyJson().extractingPath("$[0].kind").isEqualTo("INTERVIEW");
        assertThat(timeline).bodyJson().extractingPath("$[0].interview.type").isEqualTo("TECHNICAL");
        assertThat(timeline).bodyJson().extractingPath("$[-1].kind").isEqualTo("CONTACT");
        assertThat(timeline).bodyJson().extractingPath("$[-1].contact.body").isEqualTo("Interested in a Java role?");
        assertThat(timeline).bodyJson().extractingPath("$[*].kind").asArray()
                .containsExactlyInAnyOrder("INTERVIEW", "DOCUMENT", "PERSON_ADDED", "STATUS_CHANGE", "CONTACT");
        assertThat(timeline).bodyJson().extractingPath("$[?(@.kind == 'STATUS_CHANGE')].statusChange.toStatus")
                .asArray().containsExactly("SAVED");
        assertThat(timeline).bodyJson().extractingPath("$[?(@.kind == 'PERSON_ADDED')].person.person.name")
                .asArray().containsExactly("Pat Lee");
        assertThat(timeline).bodyJson().extractingPath("$[?(@.kind == 'DOCUMENT')].document.filename")
                .asArray().containsExactly("resume.pdf");
        // Only the field the kind names is set.
        assertThat(timeline).bodyJson().extractingPath("$[0].contact").isNull();
    }

    @Test
    void anotherUsersTimelineCantBeRead() {
        var appId = read(json(mvc.post().uri("/api/applications"), """
                {"companyName": "Acme", "role": "Engineer"}
                """), "$.id");

        assertThat(as(register()).get().uri("/api/applications/{id}/timeline", appId).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    private static MvcTestResult json(MockMvcRequestBuilder request, String body) {
        var result = request.contentType(MediaType.APPLICATION_JSON).content(body).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return result;
    }

    private static String read(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
