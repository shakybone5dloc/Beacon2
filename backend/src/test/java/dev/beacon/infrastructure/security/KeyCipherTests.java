package dev.beacon.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyCipherTests {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("another-thirty-two-byte-secret!!".getBytes(StandardCharsets.UTF_8));

    private final KeyCipher cipher = new KeyCipher(KEY);

    @Test
    void roundTripsAndUsesAFreshNonceEachTime() {
        var plaintext = "sk-secret".getBytes(StandardCharsets.UTF_8);

        var first = cipher.encrypt(plaintext, "user:openai");
        var second = cipher.encrypt(plaintext, "user:openai");

        assertThat(first.ciphertext()).isNotEqualTo(plaintext);
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(cipher.decrypt(first, "user:openai")).isEqualTo(plaintext);
    }

    @Test
    void refusesCiphertextMovedToAnotherRowOrReadWithAnotherKey() {
        var sealed = cipher.encrypt("sk-secret".getBytes(StandardCharsets.UTF_8), "alice:openai");

        assertThatThrownBy(() -> cipher.decrypt(sealed, "bob:openai")).isInstanceOf(KeyCipher.UnreadableKeyException.class);
        assertThatThrownBy(() -> new KeyCipher(OTHER_KEY).decrypt(sealed, "alice:openai"))
                .isInstanceOf(KeyCipher.UnreadableKeyException.class);
    }

    @Test
    void startupFailsWithAClearMessageForAMissingOrBadKey() {
        assertThatIllegalStateException().isThrownBy(() -> new KeyCipher("")).withMessageContaining("not set");
        assertThatIllegalStateException().isThrownBy(() -> new KeyCipher("not base64!")).withMessageContaining("base64");
        assertThatIllegalStateException()
                .isThrownBy(() -> new KeyCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .withMessageContaining("32 bytes");
    }
}
