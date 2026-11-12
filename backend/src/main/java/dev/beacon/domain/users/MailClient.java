package dev.beacon.domain.users;

/** Where Beacon's "Email" buttons open a message for the user to send. */
public enum MailClient {
    /** A Gmail compose window in the browser. */
    GMAIL,
    /** A mailto link, for whatever mail program the device uses. */
    MAIL_APP
}
