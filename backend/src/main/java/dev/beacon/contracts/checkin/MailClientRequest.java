package dev.beacon.contracts.checkin;

import dev.beacon.domain.users.MailClient;
import jakarta.validation.constraints.NotNull;

public record MailClientRequest(@NotNull MailClient mailClient) {}
