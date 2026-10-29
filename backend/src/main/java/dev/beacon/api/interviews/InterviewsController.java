package dev.beacon.api.interviews;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.interviews.InterviewService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.interviews.InterviewResponse;
import dev.beacon.contracts.interviews.ScheduleInterviewRequest;
import dev.beacon.contracts.interviews.UpdateInterviewRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Tag(name = "Interviews")
@RestController
public class InterviewsController {

    private static final MediaType CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final InterviewService service;

    public InterviewsController(InterviewService service) {
        this.service = service;
    }

    @Operation(operationId = "listApplicationInterviews")
    @GetMapping("/api/applications/{applicationId}/interviews")
    public ResponseEntity<List<InterviewResponse>> list(@AuthenticationPrincipal SignedInUser user,
                                                        @PathVariable UUID applicationId) {
        return ResponseEntity.of(service.onApplication(user.id(), applicationId));
    }

    @Operation(operationId = "scheduleInterview")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping("/api/applications/{applicationId}/interviews")
    public ResponseEntity<InterviewResponse> schedule(@AuthenticationPrincipal SignedInUser user,
                                                      @PathVariable UUID applicationId,
                                                      @Valid @RequestBody ScheduleInterviewRequest request) {
        return withFieldErrors(() -> service.schedule(user.id(), applicationId, request))
                .map(created -> ResponseEntity.created(URI.create("/api/interviews/" + created.id())).body(created))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(operationId = "updateInterview")
    @PutMapping("/api/interviews/{id}")
    public ResponseEntity<InterviewResponse> update(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                    @Valid @RequestBody UpdateInterviewRequest request) {
        return ResponseEntity.of(withFieldErrors(() -> service.update(user.id(), id, request)));
    }

    @Operation(operationId = "deleteInterview")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/api/interviews/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return service.delete(user.id(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** The interview as an .ics file to add to any calendar. Linked to directly, not called from code. */
    @Operation(operationId = "getInterviewCalendar")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "text/calendar",
            schema = @Schema(type = "string")))
    @GetMapping(value = "/api/interviews/{id}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> calendar(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return service.calendar(user.id(), id)
                .map(file -> ResponseEntity.ok()
                        .contentType(CALENDAR)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename(file.fileName()).build().toString())
                        .body(file.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** An unknown time zone, or another user's person as an interviewer, is a field error. */
    private static <T> T withFieldErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (InterviewService.InvalidFieldException ex) {
            throw new RequestValidationException(ex.field(), ex.getMessage());
        }
    }
}
