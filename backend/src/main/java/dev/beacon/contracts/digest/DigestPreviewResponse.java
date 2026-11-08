package dev.beacon.contracts.digest;

/** The email as it would go out now: its subject, HTML body and plain-text body. */
public record DigestPreviewResponse(String subject, String html, String text) {}
