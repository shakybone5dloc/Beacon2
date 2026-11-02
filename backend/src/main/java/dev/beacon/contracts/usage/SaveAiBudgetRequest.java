package dev.beacon.contracts.usage;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/** @param monthlyUsd null removes the budget */
public record SaveAiBudgetRequest(
        @Nullable @DecimalMin(value = "1.00", message = "Set a budget of at least $1.")
        @DecimalMax(value = "100000", message = "Set a budget of at most $100,000.")
        @Digits(integer = 6, fraction = 2, message = "Use dollars and cents, like 20 or 12.50.")
        BigDecimal monthlyUsd) {}
