package dev.beacon.application.providers;

import dev.beacon.application.providers.Provider.Cost;
import dev.beacon.application.providers.Provider.Field;
import dev.beacon.application.providers.Provider.Kind;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Every provider Beacon knows, with what it costs and how to get a key. */
@Component
public class ProviderCatalog {

    public static final String ADZUNA = "adzuna";
    public static final String USAJOBS = "usajobs";
    public static final String HIMALAYAS = "himalayas";
    public static final String OPENAI = "openai";
    public static final String ANTHROPIC = "anthropic";

    private static final List<Provider> PROVIDERS = List.of(
            new Provider("remotive", "Remotive", Kind.JOB_SOURCE, Cost.FREE,
                    "Remote jobs, mostly tech. Listings appear 24 hours after posting.",
                    "https://remotive.com", "Free, and Beacon searches it without a key.", List.of(), List.of()),
            new Provider("remoteok", "Remote OK", Kind.JOB_SOURCE, Cost.FREE,
                    "Remote jobs across tech and non-tech roles.",
                    "https://remoteok.com", "Free, and Beacon searches it without a key.", List.of(), List.of()),
            new Provider("jobicy", "Jobicy", Kind.JOB_SOURCE, Cost.FREE,
                    "Remote jobs posted in the last seven days.",
                    "https://jobicy.com", "Free, and Beacon searches it without a key.", List.of(), List.of()),
            new Provider(HIMALAYAS, "Himalayas", Kind.JOB_SOURCE, Cost.FREE,
                    "Remote jobs with salary and location details.",
                    "https://himalayas.app/api",
                    "Free. Works without a key; a key gives you your own rate limit instead of the shared one.",
                    List.of(
                            "Optional: open Himalayas' API page and request a key.",
                            "Paste the key here. Without one, Beacon still searches Himalayas."),
                    List.of(new Field("apiKey", "API key", true, false))),
            new Provider(USAJOBS, "USAJOBS", Kind.JOB_SOURCE, Cost.FREE,
                    "US federal government jobs, with application deadlines.",
                    "https://developer.usajobs.gov/apirequest/",
                    "Free, but needs a key.",
                    List.of(
                            "Fill in the API key request form at developer.usajobs.gov with your email address.",
                            "USAJOBS emails you the key, usually within a day.",
                            "Paste the key here along with the same email address; USAJOBS checks that they match."),
                    List.of(
                            new Field("apiKey", "API key", true, true),
                            new Field("email", "Email the key was sent to", false, true))),
            new Provider(ADZUNA, "Adzuna", Kind.JOB_SOURCE, Cost.PAID,
                    "Aggregated ads from many boards and employers, on-site and remote.",
                    "https://developer.adzuna.com",
                    "Paid plans for API access; trial keys are tightly limited.",
                    List.of(
                            "Create an account at developer.adzuna.com.",
                            "Choose a plan that includes API access.",
                            "Copy the App ID and App Key from your dashboard and paste them here."),
                    List.of(
                            new Field("appId", "App ID", false, true),
                            new Field("appKey", "App key", true, true))),
            new Provider(OPENAI, "OpenAI", Kind.AI, Cost.PAID,
                    "Drafts cover letters from a job description and your resume.",
                    "https://platform.openai.com/api-keys",
                    "Pay as you go. A cover letter draft typically costs a few cents.",
                    List.of(
                            "Sign in at platform.openai.com.",
                            "Add credit under Billing; the API is billed separately from a ChatGPT subscription.",
                            "Create a secret key under API keys and paste it here. OpenAI shows it only once."),
                    List.of(new Field("apiKey", "API key", true, true))),
            new Provider(ANTHROPIC, "Anthropic (Claude)", Kind.AI, Cost.PAID,
                    "Drafts cover letters from a job description and your resume.",
                    "https://console.anthropic.com/settings/keys",
                    "Pay as you go. A cover letter draft typically costs a few cents.",
                    List.of(
                            "Sign in to the Claude Console.",
                            "Buy credits under Billing; the API is billed separately from a Claude subscription.",
                            "Create a key under API keys and paste it here. The Console shows it only once."),
                    List.of(new Field("apiKey", "API key", true, true)))
    );

    public List<Provider> all() {
        return PROVIDERS;
    }

    public Optional<Provider> find(String id) {
        return PROVIDERS.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
