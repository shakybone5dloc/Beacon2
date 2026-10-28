package dev.beacon.api.apikeys;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.apikeys.ApiKeyService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.apikeys.ApiKeyResponse;
import dev.beacon.contracts.apikeys.SaveApiKeyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The signed-in user's own API keys. Responses say whether a key is saved, never what it is. */
@Tag(name = "API Keys")
@RestController
@RequestMapping("/api/me/api-keys")
public class ApiKeysController {

    private final ApiKeyService keys;

    public ApiKeysController(ApiKeyService keys) {
        this.keys = keys;
    }

    @Operation(operationId = "listApiKeys")
    @GetMapping
    public List<ApiKeyResponse> list(@AuthenticationPrincipal SignedInUser user) {
        return keys.list(user.id()).stream().map(ApiKeysController::toResponse).toList();
    }

    @Operation(operationId = "saveApiKey")
    @PutMapping("/{provider}")
    public ResponseEntity<ApiKeyResponse> save(
            @AuthenticationPrincipal SignedInUser user,
            @PathVariable String provider,
            @Valid @RequestBody SaveApiKeyRequest request
    ) {
        try {
            return ResponseEntity.of(keys.save(user.id(), provider, request.values()).map(ApiKeysController::toResponse));
        } catch (ApiKeyService.InvalidKeyException ex) {
            throw new RequestValidationException(ex.getField(), ex.getMessage());
        }
    }

    @Operation(operationId = "deleteApiKey")
    @ApiResponse(responseCode = "204")
    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SignedInUser user, @PathVariable String provider) {
        return keys.delete(user.id(), provider)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static ApiKeyResponse toResponse(ApiKeyService.KeyStatus status) {
        return new ApiKeyResponse(status.provider(), status.saved(), status.hint(), status.updatedAt());
    }
}
