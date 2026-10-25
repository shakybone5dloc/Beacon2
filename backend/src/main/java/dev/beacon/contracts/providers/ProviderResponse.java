package dev.beacon.contracts.providers;

import java.util.List;

/**
 * @param kind      JOB_SOURCE or AI
 * @param cost      FREE or PAID
 * @param needsKey  true when the provider can't be used without the fields filled in
 * @param turnedOff true when this server has switched the provider off for everyone
 */
public record ProviderResponse(
        String id,
        String name,
        String kind,
        String cost,
        boolean needsKey,
        boolean turnedOff,
        String coverage,
        String signUpUrl,
        String pricing,
        List<String> steps,
        List<FieldResponse> fields
) {

    public record FieldResponse(String name, String label, boolean secret, boolean required) { }
}
