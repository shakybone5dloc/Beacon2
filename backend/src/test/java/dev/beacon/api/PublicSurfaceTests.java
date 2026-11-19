package dev.beacon.api;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What anyone can reach without signing in, with production's settings (L4): the API
 * description and Swagger UI are off, the health check says only UP or DOWN, and the contact
 * address is there for the feedback link and the privacy and terms pages.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class PublicSurfaceTests {

    @Autowired MockMvcTester mvc;

    @Test
    void theApiDescriptionAndSwaggerUiAreOff() {
        assertThat(mvc.get().uri("/v3/api-docs").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/swagger-ui/index.html").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/swagger-ui.html").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theHealthCheckSaysOnlyWhetherItsUp() {
        var health = mvc.get().uri("/actuator/health").exchange();

        assertThat(health).hasStatus(HttpStatus.OK);
        assertThat(health).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(health).bodyJson().doesNotHavePath("$.components");
        // The container's health check still works.
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).hasStatus(HttpStatus.OK);
    }

    @Test
    void theContactAddressIsPublicAndFallsBackToTheFirstAdmin() {
        // The test configuration lists x@example.test as the admin and sets no contact address.
        assertThat(mvc.get().uri("/api/site").exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.contactEmail").isEqualTo("x@example.test");
    }
}
