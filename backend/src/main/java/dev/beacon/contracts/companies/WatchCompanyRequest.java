package dev.beacon.contracts.companies;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param url the company's careers page on Greenhouse, Lever, Ashby, Workday or Oracle, or its own page that links to one, e.g. https://jobs.lever.co/acme */
public record WatchCompanyRequest(
        @NotBlank @Size(max = 2048) String url
) {
}
