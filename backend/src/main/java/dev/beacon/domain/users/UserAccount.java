package dev.beacon.domain.users;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Someone who can sign in to Beacon. Named to avoid clashing with Spring Security's {@code User}. */
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Column(length = 100)
    private String displayName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** An optional monthly AI budget in US dollars; null means none. */
    @Column(name = "ai_monthly_budget_usd", precision = 10, scale = 2)
    private BigDecimal aiMonthlyBudgetUsd;

    /** The address the user sends emails from; null to use the one they sign in with. */
    @Column(name = "send_from_email", length = 320)
    private String sendFromEmail;

    protected UserAccount() { }

    /** @param passwordHash already encoded; the domain never sees a plain password */
    public static UserAccount register(String email, String passwordHash, String displayName, Instant now) {
        if (email == null || email.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Email and password hash are required");
        }
        var user = new UserAccount();
        user.id = UUID.randomUUID();
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.displayName = displayName == null || displayName.isBlank() ? null : displayName.strip();
        user.createdAt = now;
        return user;
    }

    /** Emails compare case-insensitively, so they are stored lower-cased. */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getAiMonthlyBudgetUsd() { return aiMonthlyBudgetUsd; }
    /** The address set to send from; null when it's the sign-in address. */
    public String getSendFromEmail() { return sendFromEmail; }

    /** The address emails are sent from: the one set, or else the one the user signs in with. */
    public String sendFrom() { return sendFromEmail == null ? email : sendFromEmail; }

    /**
     * @param address an email address, or null or blank (or the sign-in address itself) to
     *                send from the sign-in address
     */
    public void setSendFromEmail(String address) {
        var normalized = address == null || address.isBlank() ? null : normalizeEmail(address);
        this.sendFromEmail = email.equals(normalized) ? null : normalized;
    }

    /** True for a Gmail address, whose emails open in Gmail as that account. */
    public static boolean isGmail(String address) {
        var at = address == null ? -1 : address.lastIndexOf('@');
        if (at < 0) return false;
        var domain = address.substring(at + 1).toLowerCase(Locale.ROOT);
        return domain.equals("gmail.com") || domain.equals("googlemail.com");
    }


    /** @param budgetUsd null to remove the budget */
    public void setAiMonthlyBudget(BigDecimal budgetUsd) {
        if (budgetUsd != null && budgetUsd.signum() <= 0) {
            throw new IllegalArgumentException("A budget must be more than zero");
        }
        this.aiMonthlyBudgetUsd = budgetUsd;
    }
}
