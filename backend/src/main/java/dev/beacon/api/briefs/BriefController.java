package dev.beacon.api.briefs;

import dev.beacon.application.briefs.BriefService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.briefs.BriefResponse;
import dev.beacon.contracts.briefs.BriefSectionResponse;
import dev.beacon.contracts.briefs.ChooseBriefResumeRequest;
import dev.beacon.contracts.briefs.SaveBriefSectionRequest;
import dev.beacon.domain.briefs.SectionKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** An application's interview brief: its sections, written by the AI or by hand. */
@Tag(name = "Interview briefs")
@RestController
@RequestMapping("/api/applications/{applicationId}/brief")
public class BriefController {

    private final BriefService briefs;

    public BriefController(BriefService briefs) {
        this.briefs = briefs;
    }

    @Operation(operationId = "getBrief")
    @GetMapping
    public ResponseEntity<BriefResponse> get(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID applicationId) {
        return ResponseEntity.of(briefs.get(user.id(), applicationId));
    }

    @Operation(operationId = "chooseBriefResume")
    @PutMapping("/resume")
    public ResponseEntity<BriefResponse> chooseResume(@AuthenticationPrincipal SignedInUser user,
                                                      @PathVariable UUID applicationId,
                                                      @Valid @RequestBody ChooseBriefResumeRequest body) {
        return ResponseEntity.of(briefs.chooseResume(user.id(), applicationId, body.resumeDocumentId()));
    }

    /**
     * Has the AI write one section. A section with the user's edits answers 409 unless
     * {@code replaceEdits} is true.
     *
     * @param interviewId the round, for a round's update; left out otherwise
     */
    @Operation(operationId = "writeBriefSection")
    @PostMapping("/sections/{kind}/write")
    public ResponseEntity<BriefSectionResponse> write(@AuthenticationPrincipal SignedInUser user,
                                                      @PathVariable UUID applicationId, @PathVariable SectionKind kind,
                                                      @RequestParam(required = false) UUID interviewId,
                                                      @RequestParam(defaultValue = "false") boolean replaceEdits) {
        return ResponseEntity.of(briefs.write(user.id(), applicationId, kind, interviewId, replaceEdits));
    }

    /** Saves the user's own text for a section. */
    @Operation(operationId = "saveBriefSection")
    @PutMapping("/sections/{kind}")
    public ResponseEntity<BriefSectionResponse> save(@AuthenticationPrincipal SignedInUser user,
                                                     @PathVariable UUID applicationId, @PathVariable SectionKind kind,
                                                     @RequestParam(required = false) UUID interviewId,
                                                     @Valid @RequestBody SaveBriefSectionRequest body) {
        return ResponseEntity.of(briefs.edit(user.id(), applicationId, kind, interviewId, body.content(), body.version()));
    }
}
