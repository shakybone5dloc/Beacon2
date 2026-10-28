package dev.beacon.api.contacts;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.contacts.ContactService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.contacts.ContactResponse;
import dev.beacon.contracts.contacts.CreateContactRequest;
import dev.beacon.contracts.contacts.UpdateContactRequest;
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
import java.util.function.Supplier;

/** The outreach log: messages, texts and calls with people. */
@Tag(name = "Contacts")
@RestController
public class ContactsController {

    private final ContactService service;

    public ContactsController(ContactService service) {
        this.service = service;
    }

    @Operation(operationId = "listPersonContacts")
    @GetMapping("/api/people/{personId}/contacts")
    public ResponseEntity<List<ContactResponse>> withPerson(@AuthenticationPrincipal SignedInUser user,
                                                            @PathVariable UUID personId) {
        return ResponseEntity.of(service.withPerson(user.id(), personId));
    }

    @Operation(operationId = "listApplicationContacts")
    @GetMapping("/api/applications/{applicationId}/contacts")
    public ResponseEntity<List<ContactResponse>> aboutApplication(@AuthenticationPrincipal SignedInUser user,
                                                                  @PathVariable UUID applicationId) {
        return ResponseEntity.of(service.aboutApplication(user.id(), applicationId));
    }

    @Operation(operationId = "createContact")
    @ApiResponse(responseCode = "201", useReturnTypeSchema = true)
    @PostMapping("/api/contacts")
    public ResponseEntity<ContactResponse> create(@AuthenticationPrincipal SignedInUser user,
                                                  @Valid @RequestBody CreateContactRequest request) {
        var created = withReferenceErrors(() -> service.create(user.id(), request));
        return ResponseEntity.created(URI.create("/api/contacts/" + created.id())).body(created);
    }

    @Operation(operationId = "updateContact")
    @PutMapping("/api/contacts/{id}")
    public ResponseEntity<ContactResponse> update(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id,
                                                  @Valid @RequestBody UpdateContactRequest request) {
        return ResponseEntity.of(withReferenceErrors(() -> service.update(user.id(), id, request)));
    }

    @Operation(operationId = "deleteContact")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/api/contacts/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable UUID id) {
        return service.delete(user.id(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** Another user's person or application id reads as unknown, the same as one that doesn't exist. */
    private static <T> T withReferenceErrors(Supplier<T> action) {
        try {
            return action.get();
        } catch (ContactService.UnknownReferenceException ex) {
            throw new RequestValidationException(ex.field(), ex.getMessage());
        }
    }
}
