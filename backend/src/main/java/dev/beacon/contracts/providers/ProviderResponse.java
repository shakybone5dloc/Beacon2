package dev.beacon.contracts.providers;

import java.util.List;

/**
 * @param kind     JOB_SOURCE or AI
 * @param cost     FREE or PAID
 * @param needsKey true when the provider can't be used without the fields filled in
 */
public record ProviderResponse(
        String id,
        String name,
        String kind,
        String cost,
        boolean needsKey,
        String coverage,
        String signUpUrl,
        String pricing,
        List<String> steps,
        List<FieldResponse> fields
) {

    public record FieldResponse(String name, String label, boolean secret, boolean required) { }
}
