package dev.beacon.contracts.applications;

import java.util.UUID;

/**
 * How create and update requests name the employer: an existing company's id, or a
 * name that is matched to one of the user's companies or creates it. Neither means
 * the employer isn't disclosed.
 */
public interface CompanyChoice {
    UUID companyId();
    String companyName();
}
