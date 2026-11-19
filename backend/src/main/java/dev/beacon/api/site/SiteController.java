package dev.beacon.api.site;

import dev.beacon.contracts.site.SiteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * Public facts about this Beacon, for the feedback link and the privacy and terms pages. The
 * contact address is {@code beacon.contact-email}, or the first admin email when that's not set.
 */
@Tag(name = "Site")
@RestController
@RequestMapping("/api/site")
public class SiteController {

    private final String contactEmail;

    public SiteController(@Value("${beacon.contact-email:}") String contactEmail,
                          @Value("${beacon.admin-emails:}") String adminEmails) {
        var first = Arrays.stream(adminEmails.split(",")).map(String::strip).filter(e -> !e.isEmpty()).findFirst();
        this.contactEmail = !contactEmail.isBlank() ? contactEmail.strip() : first.orElse(null);
    }

    @Operation(operationId = "getSite")
    @GetMapping
    public SiteResponse site() {
        return new SiteResponse(contactEmail);
    }
}
