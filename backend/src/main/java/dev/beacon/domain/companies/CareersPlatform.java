package dev.beacon.domain.companies;

/**
 * Careers-board hosts that publish job lists with no key. Greenhouse, Lever and Ashby
 * have public APIs; Workday and Oracle Recruiting Cloud are read through the JSON
 * their own careers pages load, which isn't a documented API and may change.
 */
public enum CareersPlatform {
    GREENHOUSE,
    LEVER,
    ASHBY,
    WORKDAY,
    ORACLE
}
