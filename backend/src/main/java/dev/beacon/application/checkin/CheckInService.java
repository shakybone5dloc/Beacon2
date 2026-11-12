package dev.beacon.application.checkin;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.checkin.CheckInDraftResponse;
import dev.beacon.contracts.checkin.CheckInRecipient;
import dev.beacon.contracts.checkin.CheckInResponse;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.contacts.ContactDirection;
import dev.beacon.domain.interviews.Interview;
import dev.beacon.domain.interviews.InterviewOutcome;
import dev.beacon.domain.users.MailClient;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.ContactRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.persistence.UserAccountRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Check-in emails: "is there any update on my application?". Beacon fills in a template (or
 * has the AI draft one), the user sends it from their own mail, and logs it as a contact.
 * Beacon never sends it.
 */
@Service
public class CheckInService {

    /** The team a check-in goes to when there's nobody on the application to write to. */
    public static final String DEFAULT_TEAM = "Hiring team";

    /** Tried in this order: a user with both keys gets OpenAI, as everywhere else. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);
    /** Recent contacts the AI gets, so it knows who said what last. */
    private static final int CONTACTS_SENT = 5;
    private static final int SNIPPET = 300;

    private final JobApplicationRepository applications;
    private final ApplicationPersonRepository links;
    private final InterviewRepository interviews;
    private final ContactRepository contacts;
    private final UserAccountRepository users;
    private final List<CheckInWriter> writers;
    private final UserCredentials credentials;
    private final AiUsageService usage;
    private final TransactionTemplate tx;
    private final Clock clock;

    public CheckInService(JobApplicationRepository applications, ApplicationPersonRepository links,
                          InterviewRepository interviews, ContactRepository contacts, UserAccountRepository users,
                          List<CheckInWriter> writers, UserCredentials credentials, AiUsageService usage,
                          PlatformTransactionManager transactions, Clock clock) {
        this.applications = applications;
        this.links = links;
        this.interviews = interviews;
        this.contacts = contacts;
        this.users = users;
        this.writers = writers;
        this.credentials = credentials;
        this.usage = usage;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /**
     * What the check-in form starts with: who can be written to, the template filled in, and
     * where the user's email opens. Empty when the application does not exist.
     */
    @Transactional(readOnly = true)
    public Optional<CheckInResponse> start(UUID ownerId, UUID applicationId, ZoneId zone) {
        return applications.findByIdAndOwnerId(applicationId, ownerId).map(application -> {
            var user = users.findById(ownerId).orElseThrow();
            var recipients = links.findAllByApplicationIdOrderByAddedAtAsc(applicationId).stream()
                    .map(link -> new CheckInRecipient(link.getPerson().getId(), link.getPerson().getName(),
                            link.getPerson().getEmail(), link.getRole()))
                    .toList();
            var now = Instant.now(clock);
            var lastInterview = lastInterview(applicationId, now);
            return new CheckInResponse(recipients, DEFAULT_TEAM, subject(application),
                    body(application, lastInterview, user.getDisplayName(), zone), user.getMailClient(),
                    aiProvider(ownerId).isPresent());
        });
    }

    /** "Checking in: Senior Java Engineer application". */
    static String subject(JobApplication application) {
        return "Checking in: " + application.getRole() + " application";
    }

    /**
     * The template: when they applied or last interviewed, that they're still interested, and
     * an offer to send anything needed. Opens with "Hello," for the form to personalize.
     */
    static String body(JobApplication application, Instant lastInterview, String sender, ZoneId zone) {
        var role = "the " + application.getRole() + " position"
                + (application.getCompanyName() == null ? "" : " at " + application.getCompanyName());
        String opening;
        if (lastInterview != null) {
            opening = "Thank you again for the interview on " + DAY.format(lastInterview.atZone(zone)) + " for "
                    + role + ". I wanted to check in and ask whether there is any update on next steps.";
        } else if (application.getAppliedAt() != null) {
            opening = "I applied for " + role + " on " + DAY.format(application.getAppliedAt().atZone(zone))
                    + ", and I wanted to check in and ask whether there is any update on my application.";
        } else {
            opening = "I'm writing about " + role
                    + ", and I wanted to check in and ask whether there is any update on my application.";
        }
        return "Hello,\n\n" + opening + "\n\n"
                + "I'm still very interested in the role, and I'm happy to send anything else that would help.\n\n"
                + "Thank you for your time,\n" + (sender == null || sender.isBlank() ? "" : sender.strip());
    }

    private record Gathered(CheckInWriter.Input input) {}

    /**
     * Has the AI draft the email from the application's history. Empty when the application
     * does not exist.
     *
     * @param recipient who it's addressed to; blank means the default team
     * @param starting  the form's current text, to improve on
     */
    public Optional<CheckInDraftResponse> draft(UUID ownerId, UUID applicationId, String recipient, String starting,
                                                ZoneId zone) {
        var gathered = tx.execute(status -> applications.findByIdAndOwnerId(applicationId, ownerId)
                .map(application -> gather(ownerId, application, recipient, starting, zone)));
        if (gathered.isEmpty()) return Optional.empty();

        // The AI call is made outside any transaction, so it holds no database connection.
        var provider = aiProvider(ownerId).orElseThrow(() ->
                new CheckInException("Add an OpenAI or Anthropic API key under Settings to draft with AI."));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new CheckInException(ex.getMessage());
        }
        var writer = writers.stream().filter(w -> w.provider().equals(provider)).findFirst().orElseThrow();
        var email = writer.write(gathered.get().input(), apiKey,
                call -> usage.record(ownerId, AiFeature.CHECK_IN_EMAIL, call));
        return Optional.of(new CheckInDraftResponse(email.subject(), email.body()));
    }

    private Gathered gather(UUID ownerId, JobApplication application, String recipient, String starting, ZoneId zone) {
        var now = Instant.now(clock);
        record Event(Instant at, String line) {}
        var events = new ArrayList<Event>();
        if (application.getAppliedAt() != null) {
            events.add(new Event(application.getAppliedAt(), "Applied"));
        }
        for (Interview interview : interviews.findAllByApplicationIdOrderByStartsAtAsc(application.getId())) {
            if (interview.getOutcome() == InterviewOutcome.CANCELLED) continue;
            var label = interview.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " interview";
            events.add(new Event(interview.getStartsAt(), interview.getStartsAt().isAfter(now)
                    ? "Scheduled: " + label
                    : "Had a " + label + (interview.getOutcome() == null ? "" : " (" + interview.getOutcome().name().toLowerCase(Locale.ROOT) + ")")));
        }
        contacts.findAllByApplicationIdOrderByOccurredAtDesc(application.getId()).stream()
                .limit(CONTACTS_SENT)
                .forEach(contact -> {
                    var who = contact.getPerson() == null ? contact.getTeam() : contact.getPerson().getName();
                    var text = contact.getBody() == null ? "" : ": " + snippet(contact.getBody());
                    events.add(new Event(contact.getOccurredAt(),
                            (contact.getDirection() == ContactDirection.INBOUND ? who + " wrote to me" : "I wrote to " + who)
                                    + " (" + contact.getChannel().name().toLowerCase(Locale.ROOT) + ")" + text));
                });
        var history = events.stream()
                .sorted(Comparator.comparing(Event::at))
                .map(event -> DAY.format(event.at().atZone(zone)) + ": " + event.line())
                .toList();
        var sender = users.findById(ownerId).orElseThrow().getDisplayName();
        return new Gathered(new CheckInWriter.Input(application.getCompanyName(), application.getRole(),
                application.getStatus().name(), recipient == null || recipient.isBlank() ? DEFAULT_TEAM : recipient.strip(),
                sender, history, starting == null ? "" : starting.strip()));
    }

    private static String snippet(String text) {
        var flat = text.strip().replaceAll("\\s+", " ");
        return flat.length() > SNIPPET ? flat.substring(0, SNIPPET) + "…" : flat;
    }

    private Instant lastInterview(UUID applicationId, Instant now) {
        return interviews.findNextAndLast(List.of(applicationId), now, InterviewOutcome.CANCELLED).stream()
                .findFirst()
                .map(row -> (Instant) row[2])
                .orElse(null);
    }

    private Optional<String> aiProvider(UUID ownerId) {
        var saved = credentials.savedProviders(ownerId);
        return PREFERENCE.stream()
                .filter(saved::contains)
                .filter(id -> writers.stream().anyMatch(w -> w.provider().equals(id)))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public MailClient mailClient(UUID ownerId) {
        return users.findById(ownerId).orElseThrow().getMailClient();
    }

    @Transactional
    public MailClient setMailClient(UUID ownerId, MailClient mailClient) {
        users.findById(ownerId).orElseThrow().setMailClient(mailClient);
        return mailClient;
    }
}
