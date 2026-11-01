package dev.beacon.application.postings;

import dev.beacon.domain.companies.CareersPlatform;

import java.util.List;

/** Reads one company's open roles from its careers-board host. */
public interface CareersBoardClient {

    CareersPlatform platform();

    /** Throws if the board can't be read, e.g. when the id is wrong. */
    List<JobPosting> openings(String boardId, String companyName);
}
