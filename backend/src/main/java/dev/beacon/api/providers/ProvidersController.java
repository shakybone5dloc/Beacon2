package dev.beacon.api.providers;

import dev.beacon.application.postings.PostingSearchService;
import dev.beacon.application.providers.Provider;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.contracts.providers.ProviderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The provider guide: what each provider covers, what it costs and how to get a key. */
@Tag(name = "Providers")
@RestController
public class ProvidersController {

    private final ProviderCatalog catalog;
    private final PostingSearchService postings;

    public ProvidersController(ProviderCatalog catalog, PostingSearchService postings) {
        this.catalog = catalog;
        this.postings = postings;
    }

    @Operation(operationId = "listProviders")
    @GetMapping("/api/providers")
    public List<ProviderResponse> list() {
        return catalog.all().stream().map(this::toResponse).toList();
    }

    private ProviderResponse toResponse(Provider p) {
        var turnedOff = p.kind() == Provider.Kind.JOB_SOURCE && postings.isTurnedOff(p.id());
        return new ProviderResponse(p.id(), p.name(), p.kind().name(), p.cost().name(), p.needsKey(), turnedOff, p.coverage(),
                p.signUpUrl(), p.pricing(), p.steps(),
                p.fields().stream()
                        .map(f -> new ProviderResponse.FieldResponse(f.name(), f.label(), f.secret(), f.required()))
                        .toList());
    }
}
