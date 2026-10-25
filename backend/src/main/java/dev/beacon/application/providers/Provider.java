package dev.beacon.application.providers;

import java.util.List;

/**
 * A service Beacon talks to: a job source or an AI writer, and what a user needs
 * to use it. The provider guide, the API key form and the job source list all
 * read from these.
 *
 * @param fields  credentials the user enters; empty when none are needed
 * @param steps   how to get those credentials, in order; empty when none are needed
 */
public record Provider(
        String id,
        String name,
        Kind kind,
        Cost cost,
        String coverage,
        String signUpUrl,
        String pricing,
        List<String> steps,
        List<Field> fields
) {

    public enum Kind { JOB_SOURCE, AI }

    /** What it costs to use, which is separate from whether a key is needed (USAJOBS: free, but keyed). */
    public enum Cost { FREE, PAID }

    /** @param secret shown as a password input and never sent back to the browser */
    public record Field(String name, String label, boolean secret, boolean required) { }

    /** True when the provider can't be used at all until the user adds credentials. */
    public boolean needsKey() {
        return fields.stream().anyMatch(Field::required);
    }

    public boolean acceptsKey() {
        return !fields.isEmpty();
    }
}
