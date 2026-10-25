package dev.beacon.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts users' API keys with AES-256-GCM under the server's secret key.
 * The associated data ties each ciphertext to its row (user and provider), so a
 * value copied into another row fails to decrypt instead of being used.
 * Beacon refuses to start without a valid secret key.
 */
@Component
public class KeyCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public KeyCipher(@Value("${beacon.secret-key:}") String secretKey) {
        this.key = new SecretKeySpec(decodeKey(secretKey), "AES");
    }

    public Sealed encrypt(byte[] plaintext, String associatedData) {
        var nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            var cipher = cipher(Cipher.ENCRYPT_MODE, nonce, associatedData);
            return new Sealed(cipher.doFinal(plaintext), nonce);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt", ex);
        }
    }

    /** Throws {@link UnreadableKeyException} when the secret key changed or the data was tampered with. */
    public byte[] decrypt(Sealed sealed, String associatedData) {
        try {
            return cipher(Cipher.DECRYPT_MODE, sealed.nonce(), associatedData).doFinal(sealed.ciphertext());
        } catch (GeneralSecurityException ex) {
            throw new UnreadableKeyException();
        }
    }

    private Cipher cipher(int mode, byte[] nonce, String associatedData) throws GeneralSecurityException {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    static byte[] decodeKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("BEACON_SECRET_KEY is not set. Add 32 random bytes, base64-encoded, to .env "
                    + "(for example the output of: openssl rand -base64 32).");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secretKey.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("BEACON_SECRET_KEY is not valid base64.");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("BEACON_SECRET_KEY must be 32 bytes once decoded; it is " + bytes.length + ".");
        }
        return bytes;
    }

    public record Sealed(byte[] ciphertext, byte[] nonce) { }

    /** A saved key that can no longer be read, usually because BEACON_SECRET_KEY changed. */
    public static class UnreadableKeyException extends RuntimeException {
        public UnreadableKeyException() {
            super("A saved API key could not be read. Remove it and add it again.");
        }
    }
}
