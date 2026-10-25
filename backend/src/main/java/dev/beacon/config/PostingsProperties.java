package dev.beacon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Credentials for the job sources that need them. Blank values leave that source switched off. */
@ConfigurationProperties("beacon.postings")
public record PostingsProperties(
        String userAgent,
        Adzuna adzuna,
        UsaJobs usajobs,
        Himalayas himalayas
) {

    public PostingsProperties {
        userAgent = userAgent == null || userAgent.isBlank() ? "Beacon personal job tracker" : userAgent;
        adzuna = adzuna == null ? new Adzuna(null, null, null) : adzuna;
        usajobs = usajobs == null ? new UsaJobs(null, null) : usajobs;
        himalayas = himalayas == null ? new Himalayas(null) : himalayas;
    }

    /** Key from developer.adzuna.com; API access is a paid plan. */
    public record Adzuna(String appId, String appKey, String country) {
        public Adzuna {
            country = country == null || country.isBlank() ? "us" : country.strip().toLowerCase();
        }

        public boolean configured() {
            return hasText(appId) && hasText(appKey);
        }
    }

    /** Free key from developer.usajobs.gov; the email must be the one the key was issued to. */
    public record UsaJobs(String apiKey, String email) {
        public boolean configured() {
            return hasText(apiKey) && hasText(email);
        }
    }

    /** Optional; raises Himalayas' rate limit from the shared anonymous pool. */
    public record Himalayas(String apiKey) {
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
