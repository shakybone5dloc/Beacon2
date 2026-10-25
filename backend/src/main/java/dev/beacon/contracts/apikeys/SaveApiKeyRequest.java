package dev.beacon.contracts.apikeys;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** @param values the provider's fields by name, e.g. {"apiKey": "...", "email": "..."} */
public record SaveApiKeyRequest(@NotNull Map<String, String> values) {
}
