package dev.beacon.application.usage;

/**
 * Where an AI client reports the tokens a call used, as soon as the response arrives:
 * before reading it, so a refused or unreadable reply, which the provider still bills, is counted too.
 */
@FunctionalInterface
public interface AiUsageMeter {

    void record(AiUsage usage);

    /** Counts nothing; for callers that don't track usage. */
    AiUsageMeter NONE = usage -> { };
}
