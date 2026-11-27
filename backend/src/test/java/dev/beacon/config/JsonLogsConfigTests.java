package dev.beacon.config;

import dev.beacon.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** JSON logs are off locally, on with BEACON_JSON_LOGS=true or the prod profile. */
class JsonLogsConfigTests {

    private static final String FORMAT = "logging.structured.format.console";

    @Nested
    @SpringBootTest
    @Import(TestcontainersConfiguration.class)
    class ByDefault {
        @Autowired Environment env;

        @Test
        void keepsTheReadableFormat() {
            assertThat(env.getProperty(FORMAT)).isNullOrEmpty();
        }
    }

    @Nested
    @SpringBootTest(properties = "BEACON_JSON_LOGS=true")
    @Import(TestcontainersConfiguration.class)
    class WithTheSwitch {
        @Autowired Environment env;

        @Test
        void usesEcsJson() {
            assertThat(env.getProperty(FORMAT)).isEqualTo("ecs");
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.profiles.active=prod")
    @Import(TestcontainersConfiguration.class)
    class InProd {
        @Autowired Environment env;

        @Test
        void usesEcsJson() {
            assertThat(env.getProperty(FORMAT)).isEqualTo("ecs");
        }
    }
}
