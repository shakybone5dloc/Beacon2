package dev.beacon.contracts.coverletters;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCoverLetterRequest(
        @NotBlank @Size(max = 20_000) String text
) {
}
