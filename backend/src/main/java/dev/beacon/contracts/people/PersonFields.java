package dev.beacon.contracts.people;

import dev.beacon.contracts.applications.CompanyChoice;
import dev.beacon.domain.people.PersonRole;
import dev.beacon.domain.people.RecruiterType;

/** What create and update person requests share; the records' accessors implement it. */
public interface PersonFields extends CompanyChoice {
    String name();
    String title();
    PersonRole role();
    RecruiterType recruiterType();
    String email();
    String phone();
    String linkedinUrl();
    String notes();
}
