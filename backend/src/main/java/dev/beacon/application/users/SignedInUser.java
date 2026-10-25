package dev.beacon.application.users;

import dev.beacon.domain.users.UserAccount;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The security principal for a signed-in user. Controllers take it with
 * {@code @AuthenticationPrincipal SignedInUser user} and pass {@link #id()} on as the owner.
 * It lives in the HTTP session, so it is serializable and drops the password hash after sign-in.
 */
public final class SignedInUser implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String displayName;
    private String passwordHash;

    public SignedInUser(UUID id, String email, String displayName, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    public static SignedInUser of(UserAccount account) {
        return new SignedInUser(account.getId(), account.getEmail(), account.getDisplayName(), account.getPasswordHash());
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String displayName() { return displayName; }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public void eraseCredentials() { passwordHash = null; }
}
