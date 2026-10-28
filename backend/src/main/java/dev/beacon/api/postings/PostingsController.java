package dev.beacon.api.postings;

import dev.beacon.api.errors.RequestValidationException;
import dev.beacon.application.postings.PostingQuery;
import dev.beacon.application.postings.PostingSearchService;
import dev.beacon.application.users.SignedInUser;
import dev.beacon.contracts.postings.JobSourceResponse;
import dev.beacon.contracts.postings.PostingSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(name = "Postings")
@RestController
@RequestMapping("/api/postings")
public class PostingsController {

    private final PostingSearchService service;

    public PostingsController(PostingSearchService service) {
        this.service = service;
    }

    @Operation(operationId = "listJobSources")
    @GetMapping("/sources")
    public List<JobSourceResponse> sources(@AuthenticationPrincipal SignedInUser user) {
        return service.sources(user.id());
    }

    /** {@code sources} lists source ids, repeated or comma-separated; omit it to search every source the user can. */
    @Operation(operationId = "searchPostings")
    @GetMapping("/search")
    public PostingSearchResponse search(
            @AuthenticationPrincipal SignedInUser user,
            @RequestParam(defaultValue = "") String keywords,
            @RequestParam(defaultValue = "") String location,
            @RequestParam(defaultValue = "false") boolean remoteOnly,
            @RequestParam(defaultValue = "") List<String> sources
    ) {
        if (keywords.isBlank()) {
            throw new RequestValidationException("keywords", "Enter at least one keyword");
        }
        var ids = Set.copyOf(sources.stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
        for (var id : ids) {
            if (!service.isKnownSource(id)) {
                throw new RequestValidationException("sources", "'" + id + "' is not a known job source");
            }
        }
        return service.search(user.id(), new PostingQuery(keywords, location, remoteOnly), ids);
    }
}
