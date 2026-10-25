package dev.beacon.api.providers;

import dev.beacon.application.providers.Provider;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.contracts.providers.ProviderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The provider guide: what each provider covers, what it costs and how to get a key. */
@RestController
public class ProvidersController {

    private final ProviderCatalog catalog;

    public ProvidersController(ProviderCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/providers")
    public List<ProviderResponse> list() {
        return catalog.all().stream().map(ProvidersController::toResponse).toList();
    }

    private static ProviderResponse toResponse(Provider p) {
        return new ProviderResponse(p.id(), p.name(), p.kind().name(), p.cost().name(), p.needsKey(), p.coverage(),
                p.signUpUrl(), p.pricing(), p.steps(),
                p.fields().stream()
                        .map(f -> new ProviderResponse.FieldResponse(f.name(), f.label(), f.secret(), f.required()))
                        .toList());
    }
}
