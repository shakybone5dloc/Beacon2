package dev.beacon.contracts.applications;

import java.time.LocalDate;

/** Job-ad details accepted by both create and update; the records' accessors implement it. */
public interface PostingFields {
    String location();
    Boolean remote();
    Integer salaryMin();
    Integer salaryMax();
    String salaryCurrency();
    String salaryPeriod();
    String salaryText();
    String source();
    LocalDate postedOn();
    String jobDescription();
}
