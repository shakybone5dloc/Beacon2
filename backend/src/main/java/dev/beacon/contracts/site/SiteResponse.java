package dev.beacon.contracts.site;

import jakarta.annotation.Nullable;

/** What every visitor can know about this Beacon, signed in or not. */
public record SiteResponse(
        /** Where feedback and privacy questions go; null when the server hasn't set one. */
        @Nullable String contactEmail
) {}
