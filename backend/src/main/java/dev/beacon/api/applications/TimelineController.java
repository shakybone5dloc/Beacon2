package dev.beacon.api.applications;

import dev.beacon.application.timeline.TimelineService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.timeline.TimelineEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Applications")
@RestController
public class TimelineController {

    private final TimelineService service;

    public TimelineController(TimelineService service) {
        this.service = service;
    }

    /** Everything that happened on the application, newest first. */
    @Operation(operationId = "getApplicationTimeline")
    @GetMapping("/api/applications/{id}/timeline")
    public ResponseEntity<List<TimelineEntryResponse>> timeline(@AuthenticationPrincipal SignedInUser user,
                                                                @PathVariable UUID id) {
        return ResponseEntity.of(service.of(user.id(), id));
    }
}
