package dev.beacon.config;

import dev.beacon.application.searches.SavedSearchRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Checks for saved searches that are due once a minute. Tests turn it off
 * ({@code beacon.saved-searches.scheduler-enabled: false}) and run searches themselves.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "beacon.saved-searches.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    private final SavedSearchRunner runner;

    public SchedulingConfig(SavedSearchRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${beacon.saved-searches.poll-interval:PT1M}", initialDelayString = "PT30S")
    public void runDueSearches() {
        runner.runDue();
    }
}
