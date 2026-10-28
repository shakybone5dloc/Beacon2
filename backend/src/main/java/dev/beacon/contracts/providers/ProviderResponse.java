package dev.beacon.contracts.providers;

import io.swagger.v3.oas.annotations.media.Schema;

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
        @Schema(allowableValues = {"JOB_SOURCE", "AI"})
        String kind,
        @Schema(allowableValues = {"FREE", "PAID"})
        String cost,
        boolean needsKey,
        boolean turnedOff,
        String coverage,
        String signUpUrl,
        String pricing,
        List<String> steps,
        List<FieldResponse> fields
) {

    @Schema(name = "ProviderFieldResponse")
    public record FieldResponse(String name, String label, boolean secret, boolean required) { }
}
