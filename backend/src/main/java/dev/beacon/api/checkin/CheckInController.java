package dev.beacon.api.checkin;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.checkin.CheckInService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.checkin.CheckInDraftRequest;
import dev.beacon.contracts.checkin.CheckInDraftResponse;
import dev.beacon.contracts.checkin.CheckInResponse;
import dev.beacon.contracts.checkin.MailClientRequest;
import dev.beacon.contracts.checkin.MailClientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Check-in emails ("any update on my application?"): the filled-in template, an AI draft on
 * request, and where the user's email opens. Beacon never sends the email itself.
 */
@Tag(name = "Check-in emails")
@RestController
public class CheckInController {

    private final CheckInService service;

    public CheckInController(CheckInService service) {
        this.service = service;
    }

    /** @param zone the viewer's time zone, for the dates in the template; UTC when left out */
    @Operation(operationId = "startCheckIn")
    @GetMapping("/api/applications/{applicationId}/check-in")
    public ResponseEntity<CheckInResponse> start(@AuthenticationPrincipal SignedInUser user,
                                                 @PathVariable UUID applicationId,
                                                 @RequestParam(defaultValue = "UTC") String zone) {
        return ResponseEntity.of(service.start(user.id(), applicationId, zone(zone)));
    }

    @Operation(operationId = "draftCheckIn")
    @PostMapping("/api/applications/{applicationId}/check-in/draft")
    public ResponseEntity<CheckInDraftResponse> draft(@AuthenticationPrincipal SignedInUser user,
                                                      @PathVariable UUID applicationId,
                                                      @RequestParam(defaultValue = "UTC") String zone,
                                                      @Valid @RequestBody CheckInDraftRequest body) {
        return ResponseEntity.of(service.draft(user.id(), applicationId, body.recipient(), body.body(), zone(zone)));
    }

    private static ZoneId zone(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException ex) {
            throw new RequestValidationException("zone", "'" + zone + "' is not a time zone");
        }
    }

    @Operation(operationId = "getMailClient")
    @GetMapping("/api/me/mail-client")
    public MailClientResponse mailClient(@AuthenticationPrincipal SignedInUser user) {
        return new MailClientResponse(service.mailClient(user.id()));
    }

    @Operation(operationId = "saveMailClient")
    @PutMapping("/api/me/mail-client")
    public MailClientResponse saveMailClient(@AuthenticationPrincipal SignedInUser user,
                                             @Valid @RequestBody MailClientRequest body) {
        return new MailClientResponse(service.setMailClient(user.id(), body.mailClient()));
    }
}
