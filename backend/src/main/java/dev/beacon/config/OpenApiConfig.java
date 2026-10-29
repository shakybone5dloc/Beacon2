package dev.beacon.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.annotation.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.PropertyCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI beaconOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Beacon API")
                        .version("v1")
                        .description("Job-search assistant: applications, documents, postings, cover letters."))
                // Relative, so the generated Angular client calls /api/... on whatever host serves it
                // (the dev proxy locally), instead of springdoc's guess of the server's own address.
                .servers(List.of(new Server().url("")));
    }

    /**
     * Jackson writes every record component, nulls included, so every property of a
     * response is always present. Mark them all "required" so the generated client
     * types them as `company: string` instead of `company?: string`.
     * Present is not the same as non-null: fields that can be null carry @Nullable,
     * which springdoc writes as `type: [string, "null"]`.
     */
    @Bean
    OpenApiCustomizer responsePropertiesAreRequired() {
        return openApi -> {
            var components = openApi.getComponents();
            if (components == null || components.getSchemas() == null) return;

            components.getSchemas().forEach((name, schema) -> {
                if (name.endsWith("Response") && schema.getProperties() != null) {
                    schema.setRequired(new ArrayList<>(schema.getProperties().keySet()));
                }
            });
        };
    }

    /**
     * springdoc drops @Nullable on a property that refers to another schema (a nested
     * response), since a bare $ref can't say "or null". Spell it out as
     * {@code anyOf: [{$ref}, {type: null}]}, so the client types it as `X | null`.
     */
    @Bean
    PropertyCustomizer nullableReferences() {
        return (property, type) -> {
            var annotations = type.getCtxAnnotations();
            var nullable = annotations != null && Arrays.stream(annotations).anyMatch(a -> a instanceof Nullable);
            if (!nullable || property.get$ref() == null) return property;
            return new JsonSchema().anyOf(List.of(
                    new JsonSchema().$ref(property.get$ref()),
                    new JsonSchema().types(Set.of("null"))));
        };
    }
}