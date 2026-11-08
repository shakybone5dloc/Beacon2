package dev.beacon.api.digest;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.digest.DigestService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.digest.DigestPreviewResponse;
import dev.beacon.contracts.digest.DigestSettingsResponse;
import dev.beacon.contracts.digest.SaveDigestSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;

/** The weekly digest email: when it goes out, a preview, and sending one now. */
@Tag(name = "Digest")
@RestController
@RequestMapping("/api/me/digest")
public class DigestController {

    private final DigestService digest;

    public DigestController(DigestService digest) {
        this.digest = digest;
    }

    /** @param zone the browser's time zone, used for the defaults before anything is saved */
    @Operation(operationId = "getDigestSettings")
    @GetMapping
    public DigestSettingsResponse get(@AuthenticationPrincipal SignedInUser user,
                                      @RequestParam(defaultValue = "UTC") String zone) {
        return digest.get(user.id(), zone(zone));
    }

    @Operation(operationId = "saveDigestSettings")
    @PutMapping
    public DigestSettingsResponse save(@AuthenticationPrincipal SignedInUser user,
                                       @Valid @RequestBody SaveDigestSettingsRequest body) {
        DayOfWeek day;
        try {
            day = DayOfWeek.valueOf(body.dayOfWeek());
        } catch (IllegalArgumentException ex) {
            throw new RequestValidationException("dayOfWeek", "must be a day of the week, such as MONDAY");
        }
        return digest.save(user.id(), body, day, zone(body.zone()));
    }

    @Operation(operationId = "previewDigest")
    @GetMapping("/preview")
    public DigestPreviewResponse preview(@AuthenticationPrincipal SignedInUser user,
                                         @RequestParam(defaultValue = "UTC") String zone) {
        return digest.preview(user.id(), zone(zone));
    }

    /** Sends the digest to the user now; the weekly schedule is unchanged. */
    @Operation(operationId = "sendDigestNow")
    @ApiResponse(responseCode = "204")
    @PostMapping("/send")
    public ResponseEntity<Void> send(@AuthenticationPrincipal SignedInUser user,
                                     @RequestParam(defaultValue = "UTC") String zone) {
        digest.sendNow(user.id(), zone(zone));
        return ResponseEntity.noContent().build();
    }

    private static ZoneId zone(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException ex) {
            throw new RequestValidationException("zone", "'" + zone + "' is not a time zone");
        }
    }
}
