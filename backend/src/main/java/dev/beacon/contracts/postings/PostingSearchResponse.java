package dev.beacon.contracts.postings;

import java.util.List;

public record PostingSearchResponse(
        List<JobPostingResponse> postings,
        List<SourceResultResponse> sources
) {
}
