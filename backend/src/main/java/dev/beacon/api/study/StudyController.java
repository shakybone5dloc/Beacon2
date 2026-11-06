package dev.beacon.api.study;

import dev.beacon.application.study.StudyCardMaker;
import dev.beacon.application.study.StudyService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.study.CardResponse;
import dev.beacon.contracts.study.DeckResponse;
import dev.beacon.contracts.study.MadeCardsResponse;
import dev.beacon.contracts.study.ReviewCardRequest;
import dev.beacon.contracts.study.SaveCardRequest;
import dev.beacon.contracts.study.SaveDeckRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Study decks and cards (A6): keep them, study what's due, and make drafts from a brief. */
@Tag(name = "Study cards")
@RestController
public class StudyController {

    private final StudyService study;
    private final StudyCardMaker maker;

    public StudyController(StudyService study, StudyCardMaker maker) {
        this.study = study;
        this.maker = maker;
    }

    @Operation(operationId = "listDecks")
    @GetMapping("/api/study/decks")
    public List<DeckResponse> decks(@AuthenticationPrincipal SignedInUser user) {
        return study.decks(user.id());
    }

    @Operation(operationId = "createDeck")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping("/api/study/decks")
    public ResponseEntity<DeckResponse> createDeck(@AuthenticationPrincipal SignedInUser user,
                                                   @Valid @RequestBody SaveDeckRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(study.createDeck(user.id(), body.name()));
    }

    @Operation(operationId = "getDeck")
    @GetMapping("/api/study/decks/{id}")
    public ResponseEntity<DeckResponse> deck(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(study.deck(user.id(), id));
    }

    @Operation(operationId = "renameDeck")
    @PutMapping("/api/study/decks/{id}")
    public ResponseEntity<DeckResponse> renameDeck(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                   @Valid @RequestBody SaveDeckRequest body) {
        return ResponseEntity.of(study.renameDeck(user.id(), id, body.name(), body.version()));
    }

    @Operation(operationId = "deleteDeck")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/api/study/decks/{id}")
    public ResponseEntity<Void> deleteDeck(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return study.deleteDeck(user.id(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** Every card in the deck, drafts included. */
    @Operation(operationId = "listDeckCards")
    @GetMapping("/api/study/decks/{id}/cards")
    public ResponseEntity<List<CardResponse>> cards(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(study.cards(user.id(), id));
    }

    /** Kept cards due now, most overdue first. */
    @Operation(operationId = "listDueCards")
    @GetMapping("/api/study/decks/{id}/due")
    public ResponseEntity<List<CardResponse>> due(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(study.due(user.id(), id));
    }

    @Operation(operationId = "addCard")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping("/api/study/decks/{id}/cards")
    public ResponseEntity<CardResponse> addCard(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                @Valid @RequestBody SaveCardRequest body) {
        return study.addCard(user.id(), id, body.question(), body.answer())
                .map(card -> ResponseEntity.status(HttpStatus.CREATED).body(card))
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(operationId = "keepDeckDrafts")
    @PostMapping("/api/study/decks/{id}/keep-drafts")
    public ResponseEntity<DeckResponse> keepAll(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(study.keepAll(user.id(), id));
    }

    @Operation(operationId = "updateCard")
    @PutMapping("/api/study/cards/{id}")
    public ResponseEntity<CardResponse> updateCard(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                   @Valid @RequestBody SaveCardRequest body) {
        return ResponseEntity.of(study.editCard(user.id(), id, body.question(), body.answer(), body.version()));
    }

    @Operation(operationId = "deleteCard")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/api/study/cards/{id}")
    public ResponseEntity<Void> deleteCard(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return study.deleteCard(user.id(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @Operation(operationId = "keepCard")
    @PostMapping("/api/study/cards/{id}/keep")
    public ResponseEntity<CardResponse> keep(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return ResponseEntity.of(study.keep(user.id(), id));
    }

    /** Grades a card and returns it with its next due date. */
    @Operation(operationId = "reviewCard")
    @PostMapping("/api/study/cards/{id}/review")
    public ResponseEntity<CardResponse> review(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                               @Valid @RequestBody ReviewCardRequest body) {
        return ResponseEntity.of(study.review(user.id(), id, body.grade()));
    }

    /**
     * Makes draft cards from the application's interview brief and its stories, in the
     * application's deck (created the first time).
     */
    @Operation(operationId = "makeStudyCards")
    @PostMapping("/api/applications/{applicationId}/study-cards")
    public ResponseEntity<MadeCardsResponse> make(@AuthenticationPrincipal SignedInUser user,
                                                  @PathVariable UUID applicationId) {
        return ResponseEntity.of(maker.fromBrief(user.id(), applicationId));
    }
}
