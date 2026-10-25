package dev.beacon.application.apikeys;

import dev.beacon.application.providers.Provider;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.domain.apikeys.UserApiKey;
import dev.beacon.infrastructure.persistence.UserApiKeyRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Users' own credentials for keyed providers. Values go in encrypted and only
 * come back out for Beacon's own calls to the provider, never to the browser.
 */
@Service
public class ApiKeyService implements UserCredentials {

    static final int MAX_VALUE_LENGTH = 500;
    private static final int HINT_LENGTH = 4;
    private static final TypeReference<Map<String, String>> VALUES = new TypeReference<>() { };

    private final UserApiKeyRepository keys;
    private final ProviderCatalog catalog;
    private final KeyCipher cipher;
    private final Clock clock;

    public ApiKeyService(UserApiKeyRepository keys, ProviderCatalog catalog, KeyCipher cipher, Clock clock) {
        this.keys = keys;
        this.catalog = catalog;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** One entry for each provider that takes a key, saved or not. */
    @Transactional(readOnly = true)
    public List<KeyStatus> list(UUID userId) {
        var saved = keys.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(UserApiKey::getProvider, Function.identity()));
        return catalog.all().stream()
                .filter(Provider::acceptsKey)
                .map(p -> {
                    var key = saved.get(p.id());
                    return key == null
                            ? new KeyStatus(p.id(), false, null, null)
                            : new KeyStatus(p.id(), true, key.getHint(), key.getUpdatedAt());
                })
                .toList();
    }

    /** Empty when the provider doesn't exist or takes no key. Throws {@link InvalidKeyException} for bad values. */
    @Transactional
    public Optional<KeyStatus> save(UUID userId, String providerId, Map<String, String> values) {
        var found = catalog.find(providerId).filter(Provider::acceptsKey);
        if (found.isEmpty()) return Optional.empty();
        var provider = found.get();

        var clean = validate(provider, values == null ? Map.of() : values);
        var sealed = cipher.encrypt(JsonMapper.shared().writeValueAsBytes(clean), associatedData(userId, providerId));
        var hint = hint(provider, clean);
        var now = Instant.now(clock);

        var key = keys.findByUserIdAndProvider(userId, providerId)
                .map(existing -> {
                    existing.replace(sealed.ciphertext(), sealed.nonce(), hint, now);
                    return existing;
                })
                .orElseGet(() -> keys.save(UserApiKey.create(userId, providerId, sealed.ciphertext(), sealed.nonce(), hint, now)));
        return Optional.of(new KeyStatus(providerId, true, key.getHint(), key.getUpdatedAt()));
    }

    @Transactional
    public boolean delete(UUID userId, String providerId) {
        var found = keys.findByUserIdAndProvider(userId, providerId);
        found.ifPresent(keys::delete);
        return found.isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> savedProviders(UUID userId) {
        return keys.findAllByUserId(userId).stream().map(UserApiKey::getProvider).collect(Collectors.toUnmodifiableSet());
    }

    /** The decrypted values, for Beacon's own calls to the provider. */
    @Override
    @Transactional(readOnly = true)
    public Optional<Map<String, String>> credentials(UUID userId, String providerId) {
        return keys.findByUserIdAndProvider(userId, providerId).map(key -> {
            var plaintext = cipher.decrypt(new KeyCipher.Sealed(key.getCiphertext(), key.getNonce()), associatedData(userId, providerId));
            return JsonMapper.shared().readValue(plaintext, VALUES);
        });
    }

    /** Only the provider's own fields, trimmed; every required one must be there. */
    static Map<String, String> validate(Provider provider, Map<String, String> values) {
        var known = provider.fields().stream().map(Provider.Field::name).collect(Collectors.toSet());
        for (var name : values.keySet()) {
            if (!known.contains(name)) throw new InvalidKeyException(name, provider.name() + " has no field called '" + name + "'.");
        }

        var clean = new LinkedHashMap<String, String>();
        for (var field : provider.fields()) {
            var value = values.get(field.name());
            value = value == null ? "" : value.strip();
            if (value.isEmpty()) {
                if (field.required()) throw new InvalidKeyException(field.name(), field.label() + " is required.");
                continue;
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new InvalidKeyException(field.name(), field.label() + " must be at most " + MAX_VALUE_LENGTH + " characters.");
            }
            clean.put(field.name(), value);
        }
        if (clean.isEmpty()) throw new InvalidKeyException(provider.fields().getFirst().name(), "Enter a key to save.");
        return clean;
    }

    /** The end of the first secret value, so the user can tell which key is saved. */
    static String hint(Provider provider, Map<String, String> values) {
        return provider.fields().stream()
                .filter(Provider.Field::secret)
                .map(f -> values.get(f.name()))
                .filter(v -> v != null && v.length() > HINT_LENGTH * 2)
                .map(v -> v.substring(v.length() - HINT_LENGTH))
                .findFirst()
                .orElse(null);
    }

    private static String associatedData(UUID userId, String providerId) {
        return userId + ":" + providerId;
    }

    /** @param updatedAt null when nothing is saved */
    public record KeyStatus(String provider, boolean saved, String hint, Instant updatedAt) { }

    public static class InvalidKeyException extends RuntimeException {
        private final String field;

        public InvalidKeyException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }
}
