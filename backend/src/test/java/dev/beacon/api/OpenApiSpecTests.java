package dev.beacon.api;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSpecTests {

    @Autowired MockMvcTester mvc;

    private MvcTestResult spec() {
        return mvc.get().uri("/v3/api-docs").exchange();   // no sign-in: the docs are public
    }

    @Test
    void specIsPublic() {
        assertThat(spec()).hasStatus(HttpStatus.OK);
    }

    @Test
    void everyOperationHasAStableUniqueId() {
        var spec = spec();

        assertThat(spec).bodyJson()
                .extractingPath("$.paths['/api/applications'].get.operationId")
                .isEqualTo("listApplications");

        assertThat(spec).bodyJson()
                .extractingPath("$..operationId").asArray()   // ".." = search the whole document
                .doesNotHaveDuplicates()
                .noneMatch(id -> id.toString().matches(".*_\\d+$"));   // no list_1, delete_3...
    }

    @Test
    void responsesDescribeStatusAsAnEnumAndPropertiesAsRequired() {
        var spec = spec();

        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.required").asArray()
                .contains("id", "company", "status", "version");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.status.enum").asArray()
                .contains("SAVED", "INTERVIEWING");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.allowedTransitions.items.enum").asArray()
                .contains("APPLIED");
        assertThat(spec).bodyJson()
                .extractingPath("$.components.schemas.ApplicationResponse.properties.jobDescription").asMap()
                .doesNotContainKey("enum");
    }

    /** Every handler takes @AuthenticationPrincipal SignedInUser; it comes from the session, never from the client. */
    @Test
    void theSignedInUserIsNeverAParameter() {
        assertThat(spec()).bodyJson()
                .extractingPath("$..parameters[?(@.name == 'user')]").asArray()
                .isEmpty();
    }
}