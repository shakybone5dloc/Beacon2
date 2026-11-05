package dev.beacon.api.stories;

import dev.beacon.application.stories.StoryDrafts;
import dev.beacon.application.stories.StoryService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.stories.DraftStoriesRequest;
import dev.beacon.contracts.stories.SaveStoryRequest;
import dev.beacon.contracts.stories.StoryDraftResponse;
import dev.beacon.contracts.stories.StoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** The story bank: STAR stories, and AI drafts of new ones to review before saving. */
@Tag(name = "Stories")
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService stories;
    private final StoryDrafts drafts;

    public StoryController(StoryService stories, StoryDrafts drafts) {
        this.stories = stories;
        this.drafts = drafts;
    }

    @Operation(operationId = "listStories")
    @GetMapping
    public List<StoryResponse> list(@AuthenticationPrincipal SignedInUser user) {
        return stories.list(user.id());
    }

    @Operation(operationId = "getStory")
    @GetMapping("/{id}")
    public ResponseEntity<StoryResponse> get(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(stories.get(user.id(), id));
    }

    @Operation(operationId = "createStory")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping
    public ResponseEntity<StoryResponse> create(@AuthenticationPrincipal SignedInUser user,
                                                @Valid @RequestBody SaveStoryRequest body) {
        var created = stories.create(user.id(), body);
        return ResponseEntity.created(URI.create("/api/stories/" + created.id())).body(created);
    }

    @Operation(operationId = "updateStory")
    @PutMapping("/{id}")
    public ResponseEntity<StoryResponse> update(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                @Valid @RequestBody SaveStoryRequest body) {
        return ResponseEntity.of(stories.update(user.id(), id, body));
    }

    @Operation(operationId = "deleteStory")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return stories.delete(user.id(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * Drafts with the AI, saving nothing: one story from the user's notes, or a few from their
     * resume when no notes are given. The user reviews each and saves the ones they keep.
     */
    @Operation(operationId = "draftStories")
    @PostMapping("/drafts")
    public List<StoryDraftResponse> draft(@AuthenticationPrincipal SignedInUser user,
                                          @Valid @RequestBody DraftStoriesRequest body) {
        return drafts.draft(user.id(), body.notes(), body.resumeDocumentId());
    }
}
