package dev.beacon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings shared by every user's job searches. Keys are per user; see the API keys settings. */
@ConfigurationProperties("beacon.postings")
public record PostingsProperties(
        String userAgent,
        Adzuna adzuna
) {

    public PostingsProperties {
        userAgent = userAgent == null || userAgent.isBlank() ? "Beacon personal job tracker" : userAgent;
        adzuna = adzuna == null ? new Adzuna(null) : adzuna;
    }

    /** Which Adzuna country site to search, e.g. "us" or "gb". */
    public record Adzuna(String country) {
        public Adzuna {
            country = country == null || country.isBlank() ? "us" : country.strip().toLowerCase();
        }
    }
}
