package dev.beacon.contracts.people;

import dev.beacon.domain.people.PersonRole;
import dev.beacon.domain.people.RecruiterType;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.UUID;

/**
 * @param companyId     one of the user's companies; takes precedence over companyName
 * @param companyName   matched to one of the user's companies by name, or created
 * @param recruiterType only kept when the role is RECRUITER
 */
public record CreatePersonRequest(
        @NotBlank @Size(max = 200) String name,
        @Nullable UUID companyId,
        @Nullable @Size(max = 200) String companyName,
        @Nullable @Size(max = 200) String title,
        @NotNull PersonRole role,
        @Nullable RecruiterType recruiterType,
        @Nullable @Email @Size(max = 320) String email,
        @Nullable @Size(max = 50) String phone,
        @Nullable @URL @Size(max = 2048) String linkedinUrl,
        @Nullable @Size(max = 4000) String notes
) implements PersonFields {
}
