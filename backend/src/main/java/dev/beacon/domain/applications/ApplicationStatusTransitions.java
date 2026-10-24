package dev.beacon.domain.applications;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static dev.beacon.domain.applications.ApplicationStatus.*;

public final class ApplicationStatusTransitions {

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(SAVED, EnumSet.of(APPLIED, WITHDRAWN));
        ALLOWED.put(APPLIED, EnumSet.of(INTERVIEWING, REJECTED, WITHDRAWN));
        ALLOWED.put(INTERVIEWING, EnumSet.of(OFFER, REJECTED, WITHDRAWN));
        // REJECTED from OFFER covers an offer being rescinded.
        ALLOWED.put(OFFER, EnumSet.of(ACCEPTED, DECLINED, REJECTED));
        ALLOWED.put(ACCEPTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(DECLINED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(REJECTED, EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
    }

    private ApplicationStatusTransitions() {}

    public static boolean canMove(ApplicationStatus from, ApplicationStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<ApplicationStatus> allowedFrom(ApplicationStatus from) {
        return Collections.unmodifiableSet(ALLOWED.getOrDefault(from, EnumSet.noneOf(ApplicationStatus.class)));
    }
}
