package dev.beacon.api.stats;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.stats.StatsService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.stats.StatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** The job search in numbers. */
@Tag(name = "Stats")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;
    private final Clock clock;

    public StatsController(StatsService stats, Clock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    /**
     * @param days only applications created in the last this many days; all of them when left out
     * @param zone the browser's time zone, for which week an application went out in
     */
    @Operation(operationId = "getStats")
    @GetMapping
    public StatsResponse stats(@AuthenticationPrincipal SignedInUser user,
                               @RequestParam(required = false) Integer days,
                               @RequestParam(defaultValue = "UTC") String zone) {
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(zone);
        } catch (DateTimeException ex) {
            throw new RequestValidationException("zone", "'" + zone + "' is not a time zone");
        }
        if (days != null && (days < 1 || days > 3650)) {
            throw new RequestValidationException("days", "Pick between 1 and 3650 days");
        }
        var since = days == null ? null : Instant.now(clock).minus(Duration.ofDays(days));
        return stats.stats(user.id(), since, zoneId);
    }
}
