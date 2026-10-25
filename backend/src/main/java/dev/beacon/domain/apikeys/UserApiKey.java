package dev.beacon.domain.apikeys;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One user's encrypted credentials for one provider. */
@Entity
@Table(name = "user_api_keys")
@IdClass(UserApiKey.Key.class)
public class UserApiKey {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(length = 50)
    private String provider;

    @Column(nullable = false)
    private byte[] ciphertext;

    @Column(nullable = false)
    private byte[] nonce;

    @Column(length = 8)
    private String hint;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserApiKey() { }

    public static UserApiKey create(UUID userId, String provider, byte[] ciphertext, byte[] nonce, String hint, Instant now) {
        var key = new UserApiKey();
        key.userId = userId;
        key.provider = provider;
        key.createdAt = now;
        key.replace(ciphertext, nonce, hint, now);
        return key;
    }

    public void replace(byte[] ciphertext, byte[] nonce, String hint, Instant now) {
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.hint = hint;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public byte[] getCiphertext() { return ciphertext; }
    public byte[] getNonce() { return nonce; }
    public String getHint() { return hint; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class Key implements Serializable {
        private UUID userId;
        private String provider;

        protected Key() { }

        public Key(UUID userId, String provider) {
            this.userId = userId;
            this.provider = provider;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(userId, other.userId) && Objects.equals(provider, other.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, provider);
        }
    }
}
