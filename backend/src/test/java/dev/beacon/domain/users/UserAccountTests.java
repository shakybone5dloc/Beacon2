package dev.beacon.domain.users;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTests {

    @Test
    void knowsAGmailAddress() {
        assertThat(UserAccount.isGmail("job.hunt@gmail.com")).isTrue();
        assertThat(UserAccount.isGmail("Job.Hunt@GMAIL.COM")).isTrue();
        assertThat(UserAccount.isGmail("old@googlemail.com")).isTrue();
        assertThat(UserAccount.isGmail("me@work.example")).isFalse();
        assertThat(UserAccount.isGmail("me@notgmail.com")).isFalse();
        assertThat(UserAccount.isGmail("gmail.com")).isFalse();
        assertThat(UserAccount.isGmail(null)).isFalse();
    }

    @Test
    void sendsFromTheSignInAddressUnlessAnotherIsSet() {
        var user = UserAccount.register("Me@Example.test", "hash", null, Instant.EPOCH);
        assertThat(user.sendFrom()).isEqualTo("me@example.test");

        user.setSendFromEmail("  Job.Hunt@gmail.com ");
        assertThat(user.sendFrom()).isEqualTo("job.hunt@gmail.com");

        // Setting the sign-in address again is the same as clearing it.
        user.setSendFromEmail("ME@example.test");
        assertThat(user.getSendFromEmail()).isNull();
        user.setSendFromEmail("x@gmail.com");
        user.setSendFromEmail(" ");
        assertThat(user.sendFrom()).isEqualTo("me@example.test");
    }
}
