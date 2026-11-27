package dev.beacon.api;

import dev.beacon.SignedInApiTests;
import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/** Unexpected errors: a 500 body carries the request id, and the JSON log line has requestId and userId, no email. */
@SpringBootTest(properties = "logging.structured.format.console=ecs")
@Import({TestcontainersConfiguration.class, ServerErrorLogTests.Boom.class})
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class ServerErrorLogTests extends SignedInApiTests {

    @Autowired ConfigurableEnvironment env;

    /** A shared test JVM has already set up logging, so apply the JSON format explicitly, then restore it. */
    @BeforeEach
    void useJsonLogs() {
        reinitialize(env);
    }

    @AfterEach
    void restoreLogs() {
        reinitialize(new MockEnvironment());
    }

    private void reinitialize(ConfigurableEnvironment environment) {
        var system = LoggingSystem.get(getClass().getClassLoader());
        system.cleanUp();
        system.beforeInitialize();
        system.initialize(new LoggingInitializationContext(environment), null, null);
    }

    @RestController
    static class Boom {
        @GetMapping("/api/test-only/boom")
        String boom() {
            throw new IllegalStateException("kaboom");
        }
    }

    @Test
    void a500CarriesTheRequestIdInBodyAndJsonLog(CapturedOutput output) throws Exception {
        var result = mvc.get().uri("/api/test-only/boom").header("X-Request-Id", "boom-1").exchange();

        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("boom-1");
        assertThat(result).bodyJson().extractingPath("$.requestId").isEqualTo("boom-1");

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode line = null;
        for (var l : output.getAll().split("\\R")) {
            if (l.startsWith("{") && l.contains("Unhandled exception")) {
                line = mapper.readTree(l);
            }
        }
        assertThat(line).as("JSON log line for the failure").isNotNull();
        assertThat(line.path("requestId").asText()).isEqualTo("boom-1");
        assertThat(line.path("userId").asText()).isEqualTo(signedIn.id().toString());
        assertThat(output.getAll()).doesNotContain("@example.com");
    }
}
