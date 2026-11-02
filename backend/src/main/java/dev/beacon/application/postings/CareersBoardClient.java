package dev.beacon.application.postings;

import dev.beacon.domain.companies.CareersPlatform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reads one company's open roles from its careers-board host. */
public interface CareersBoardClient {

    CareersPlatform platform();

    /**
     * What's open, for the company page. Hosts with very large boards return only
     * the newest roles. Throws if the board can't be read, e.g. when the id is wrong.
     */
    List<JobPosting> openings(String boardId, String companyName);

    /**
     * Open roles matching every keyword. The default reads every opening and matches the
     * full text here, since titles rarely name the stack ("Java"); hosts with boards too
     * large to read whole search on the host instead.
     */
    default List<JobPosting> search(String boardId, String companyName, String keywords) {
        return openings(boardId, companyName).stream()
                .filter(p -> PostingMatcher.matchesKeywords(keywords, fullText(p)))
                .toList();
    }

    private static List<String> fullText(JobPosting posting) {
        var texts = new ArrayList<String>(posting.tags());
        texts.addAll(Arrays.asList(posting.title(), posting.company(), posting.snippet(), posting.description()));
        return texts;
    }
}
