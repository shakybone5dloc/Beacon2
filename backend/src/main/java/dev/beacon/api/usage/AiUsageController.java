package dev.beacon.api.usage;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.usage.AiUsageResponse;
import dev.beacon.contracts.usage.SaveAiBudgetRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** What the signed-in user has spent on AI, and their optional monthly budget. */
@Tag(name = "AI Usage")
@RestController
@RequestMapping("/api/me/ai-usage")
public class AiUsageController {

    private final AiUsageService usage;

    public AiUsageController(AiUsageService usage) {
        this.usage = usage;
    }

    /** @param zone the browser's time zone, so "this month" starts when the user's month does; UTC when left out */
    @Operation(operationId = "getAiUsage")
    @GetMapping
    public AiUsageResponse summary(@AuthenticationPrincipal SignedInUser user,
                                   @RequestParam(required = false) String zone) {
        return usage.summary(user.id(), zone(zone));
    }

    @Operation(operationId = "saveAiBudget")
    @PutMapping("/budget")
    public AiUsageResponse saveBudget(@AuthenticationPrincipal SignedInUser user,
                                      @RequestParam(required = false) String zone,
                                      @Valid @RequestBody SaveAiBudgetRequest request) {
        var zoneId = zone(zone);
        usage.setBudget(user.id(), request.monthlyUsd());
        return usage.summary(user.id(), zoneId);
    }

    private static ZoneId zone(String zone) {
        if (zone == null || zone.isBlank()) return ZoneOffset.UTC;
        try {
            return ZoneId.of(zone.strip());
        } catch (DateTimeException ex) {
            throw new RequestValidationException("zone", "Unknown time zone: " + zone);
        }
    }
}
