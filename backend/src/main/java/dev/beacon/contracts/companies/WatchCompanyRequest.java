package dev.beacon.contracts.companies;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param url the company's careers page on Greenhouse, Lever or Ashby, e.g. https://jobs.lever.co/acme */
public record WatchCompanyRequest(
        @NotBlank @Size(max = 2048) String url
) {
}
