package dev.beacon.config;

import dev.beacon.application.digest.DigestService;
import dev.beacon.application.searches.SavedSearchRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Background work: saved searches that are due, checked once a minute, and weekly digests,
 * checked every few minutes. Tests turn both off ({@code beacon.saved-searches.scheduler-enabled: false})
 * and run them themselves.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "beacon.saved-searches.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    private final SavedSearchRunner runner;
    private final DigestService digests;

    public SchedulingConfig(SavedSearchRunner runner, DigestService digests) {
        this.runner = runner;
        this.digests = digests;
    }

    @Scheduled(fixedDelayString = "${beacon.saved-searches.poll-interval:PT1M}", initialDelayString = "PT30S")
    public void runDueSearches() {
        runner.runDue();
    }

    @Scheduled(fixedDelayString = "${beacon.digest.poll-interval:PT5M}", initialDelayString = "PT1M")
    public void sendDueDigests() {
        digests.sendDue();
    }
}
