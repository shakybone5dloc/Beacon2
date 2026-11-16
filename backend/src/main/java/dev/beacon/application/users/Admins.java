package dev.beacon.application.users;

import dev.beacon.domain.users.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who can manage Beacon itself (invite codes, for now): the accounts whose emails are listed in
 * {@code beacon.admin-emails}. Set in the server's configuration, never through the app.
 */
@Component
public class Admins {

    private final Set<String> emails;

    public Admins(@Value("${beacon.admin-emails:}") String emails) {
        this.emails = Arrays.stream(emails.split(","))
                .map(String::strip)
                .filter(email -> !email.isEmpty())
                .map(UserAccount::normalizeEmail)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return email != null && emails.contains(UserAccount.normalizeEmail(email));
    }
}
