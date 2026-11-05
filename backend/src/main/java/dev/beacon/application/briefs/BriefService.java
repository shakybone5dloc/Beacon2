package dev.beacon.application.briefs;

import dev.beacon.application.apikeys.UserCredentials;
import dev.beacon.application.briefs.BriefWriter.BriefInput;
import dev.beacon.application.documents.DocumentService;
import dev.beacon.application.fit.FitService;
import dev.beacon.application.providers.ProviderCatalog;
import dev.beacon.application.resumes.ResumeService;
import dev.beacon.application.stories.StoryService;
import dev.beacon.application.usage.AiFeature;
import dev.beacon.application.usage.AiUsageMeter;
import dev.beacon.application.usage.AiUsageService;
import dev.beacon.contracts.briefs.BriefResponse;
import dev.beacon.contracts.briefs.BriefSectionResponse;
import dev.beacon.contracts.resumes.ResumeResponse;
import dev.beacon.domain.applications.JobApplication;
import dev.beacon.domain.briefs.BriefSection;
import dev.beacon.domain.briefs.InterviewBrief;
import dev.beacon.domain.briefs.SectionKind;
import dev.beacon.domain.documents.Document;
import dev.beacon.domain.interviews.Interview;
import dev.beacon.domain.interviews.InterviewOutcome;
import dev.beacon.infrastructure.documents.DocumentText;
import dev.beacon.infrastructure.persistence.ApplicationPersonRepository;
import dev.beacon.infrastructure.persistence.BriefSectionRepository;
import dev.beacon.infrastructure.persistence.InterviewBriefRepository;
import dev.beacon.infrastructure.persistence.InterviewRepository;
import dev.beacon.infrastructure.persistence.JobApplicationRepository;
import dev.beacon.infrastructure.security.KeyCipher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * An application's interview brief (A1): a section at a time, each written by the AI from
 * everything Beacon knows about the application, or by the user. Only written or edited
 * sections are stored; the rest come back empty, the checklist with its template.
 */
@Service
public class BriefService {

    /** Tried in this order: a user with both keys gets OpenAI, as with cover letters and fit. */
    private static final List<String> PREFERENCE = List.of(ProviderCatalog.OPENAI, ProviderCatalog.ANTHROPIC);

    private static final DateTimeFormatter ROUND_TIME =
            DateTimeFormatter.ofPattern("EEE MMM d, yyyy h:mm a", Locale.US);

    static final String CHECKLIST_TEMPLATE = """
            - [ ] Reread this brief, the posting and your resume
            - [ ] Test the meeting link, camera and microphone, or plan the route and parking
            - [ ] Water, notes and a copy of your resume within reach
            - [ ] Questions to ask, written down
            - [ ] Close other apps and silence notifications
            - [ ] Send a thank-you note within a day""";

    private final JobApplicationRepository applications;
    private final InterviewBriefRepository briefs;
    private final BriefSectionRepository sections;
    private final InterviewRepository interviews;
    private final ApplicationPersonRepository people;
    private final DocumentService documents;
    private final ResumeService resumes;
    private final FitService fit;
    private final StoryService stories;
    private final List<BriefWriter> writers;
    private final List<CompanyResearcher> researchers;
    private final UserCredentials credentials;
    private final AiUsageService usage;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BriefService(JobApplicationRepository applications, InterviewBriefRepository briefs,
                        BriefSectionRepository sections, InterviewRepository interviews,
                        ApplicationPersonRepository people, DocumentService documents, ResumeService resumes,
                        FitService fit, StoryService stories, List<BriefWriter> writers,
                        List<CompanyResearcher> researchers, UserCredentials credentials,
                        AiUsageService usage, PlatformTransactionManager transactions, Clock clock) {
        this.applications = applications;
        this.briefs = briefs;
        this.sections = sections;
        this.interviews = interviews;
        this.people = people;
        this.documents = documents;
        this.resumes = resumes;
        this.fit = fit;
        this.stories = stories;
        this.writers = writers;
        this.researchers = researchers;
        this.credentials = credentials;
        this.usage = usage;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    /** Every section in reading order. Empty when the application does not exist. */
    public Optional<BriefResponse> get(UUID ownerId, UUID applicationId) {
        return read(() -> applications.findByIdAndOwnerId(applicationId, ownerId).map(application -> {
            var brief = briefs.findByApplicationIdAndOwnerId(applicationId, ownerId);
            var stored = brief.map(b -> sections.findAllByBriefId(b.getId())).orElse(List.of());
            var resume = resume(ownerId, applicationId, brief);
            return new BriefResponse(resume.map(Document::getId).orElse(null),
                    resume.map(r -> resumes.label(ownerId, r.getId()).orElse(r.getFilename())).orElse(null),
                    chooseProvider(credentials.savedProviders(ownerId)).isPresent(),
                    layout(applicationId, stored));
        }));
    }

    /**
     * Writes one section with the AI. A section the user has edited is only replaced when
     * {@code replaceEdits} says so. Empty when the application or the round does not exist.
     */
    public Optional<BriefSectionResponse> write(UUID ownerId, UUID applicationId, SectionKind kind, UUID interviewId,
                                                boolean replaceEdits) {
        if (!kind.isWritten()) {
            throw new BriefException("This section is yours to fill in; the AI doesn't write it.", false);
        }
        if (kind.isPerRound() != (interviewId != null)) {
            throw new BriefException(kind.isPerRound() ? "Say which interview round to write about." :
                    "Only a round's update is about one interview.", false);
        }

        // Everything is gathered first, so the slow AI call holds no database connection.
        var request = read(() -> prepare(ownerId, applicationId, kind, interviewId, replaceEdits));
        if (request.isEmpty()) return Optional.empty();
        var prepared = request.get();

        // The company section is researched on the web; the rest are written from what Beacon has.
        AiUsageMeter meter = call -> usage.record(ownerId, AiFeature.INTERVIEW_BRIEF, call);
        String markdown;
        String model;
        if (kind == SectionKind.COMPANY) {
            var researcher = researcher(prepared.provider());
            markdown = researcher.research(prepared.input(), prepared.apiKey(), meter);
            model = researcher.model();
        } else {
            var writer = writer(prepared.provider());
            markdown = writer.write(prepared.input(), prepared.apiKey(), meter);
            model = writer.model();
        }

        return Optional.of(tx.execute(status -> {
            var brief = findOrCreate(ownerId, applicationId);
            var section = sections.findOne(brief.getId(), kind, interviewId)
                    .orElseGet(() -> BriefSection.start(brief.getId(), kind, interviewId, Instant.now(clock)));
            // The user may have edited it while the AI was writing.
            if (section.isEdited() && !replaceEdits) throw new BriefException.EditedException();
            section.written(markdown, model, Instant.now(clock));
            brief.touch(Instant.now(clock));
            return toResponse(sections.saveAndFlush(section), kind, interviewId);
        }));
    }

    /** Saves the user's own text for a section. Empty when the application or the round does not exist. */
    public Optional<BriefSectionResponse> edit(UUID ownerId, UUID applicationId, SectionKind kind, UUID interviewId,
                                               String content, Long version) {
        if (kind.isPerRound() != (interviewId != null)) {
            throw new BriefException(kind.isPerRound() ? "Say which interview round this is about." :
                    "Only a round's update is about one interview.", false);
        }
        return tx.execute(status -> {
            if (applications.findByIdAndOwnerId(applicationId, ownerId).isEmpty()) return Optional.empty();
            if (interviewId != null && !isRoundOf(ownerId, applicationId, interviewId)) return Optional.empty();

            var brief = findOrCreate(ownerId, applicationId);
            var existing = sections.findOne(brief.getId(), kind, interviewId);
            var current = existing.map(BriefSection::getVersion).orElse(null);
            if (!Objects.equals(current, version)) {
                throw new OptimisticLockingFailureException("This section was changed somewhere else.");
            }
            var section = existing.orElseGet(() -> BriefSection.start(brief.getId(), kind, interviewId, Instant.now(clock)));
            section.edited(content.strip(), Instant.now(clock));
            brief.touch(Instant.now(clock));
            return Optional.of(toResponse(sections.saveAndFlush(section), kind, interviewId));
        });
    }

    /** Picks the resume sections are written from. Empty when the application does not exist. */
    public Optional<BriefResponse> chooseResume(UUID ownerId, UUID applicationId, UUID resumeDocumentId) {
        var chosen = tx.execute(status -> {
            if (applications.findByIdAndOwnerId(applicationId, ownerId).isEmpty()) return false;
            var resume = documents.find(ownerId, applicationId, resumeDocumentId)
                    .or(() -> documents.find(ownerId, null, resumeDocumentId))
                    .orElseThrow(() -> new BriefException("That resume was not found.", false));
            findOrCreate(ownerId, applicationId).useResume(resume.getId(), Instant.now(clock));
            return true;
        });
        return Boolean.TRUE.equals(chosen) ? get(ownerId, applicationId) : Optional.empty();
    }

    private record Prepared(BriefInput input, String provider, String apiKey) {}

    private Optional<Prepared> prepare(UUID ownerId, UUID applicationId, SectionKind kind, UUID interviewId,
                                       boolean replaceEdits) {
        var found = applications.findByIdAndOwnerId(applicationId, ownerId);
        if (found.isEmpty()) return Optional.empty();
        var application = found.get();
        if (interviewId != null && !isRoundOf(ownerId, applicationId, interviewId)) return Optional.empty();

        var brief = briefs.findByApplicationIdAndOwnerId(applicationId, ownerId);
        var section = brief.flatMap(b -> sections.findOne(b.getId(), kind, interviewId));
        if (section.isPresent() && section.get().isEdited() && !replaceEdits) throw new BriefException.EditedException();

        if (application.getJobDescription() == null) {
            throw new BriefException("Save the posting as a PDF or paste the job description into this application first.", false);
        }
        if (kind == SectionKind.COMPANY && application.getCompanyName() == null) {
            throw new BriefException("The employer isn't disclosed, so there's no company to research yet.", false);
        }
        var rounds = interviews.findAllByApplicationIdOrderByStartsAtAsc(applicationId);
        if (interviewId != null) {
            var round = rounds.stream().filter(i -> i.getId().equals(interviewId)).findFirst().orElseThrow();
            if (round.getDebriefNotes() == null || round.getDebriefNotes().isBlank()) {
                throw new BriefException("Add your debrief notes to this interview first; the update is written from them.", false);
            }
        }

        var resume = resume(ownerId, applicationId, brief).orElseThrow(() -> new BriefException(
                "Add a resume to your library, or pick one for this brief, to write from.", false));
        var resumeText = resumes.text(ownerId, resume.getId()).flatMap(text -> text)
                .or(() -> DocumentText.extract(resume.getContent(), resume.getContentType()))
                .orElseThrow(() -> new BriefException(
                        "Beacon can read PDF, text and Markdown resumes. Pick a resume in one of those formats.", false));

        var provider = chooseProvider(credentials.savedProviders(ownerId)).orElseThrow(() -> new BriefException(
                "Add an OpenAI or Anthropic API key under Settings to write the brief.", false));
        String apiKey;
        try {
            apiKey = credentials.credentials(ownerId, provider).map(values -> values.get("apiKey")).orElseThrow();
        } catch (KeyCipher.UnreadableKeyException ex) {
            throw new BriefException(ex.getMessage(), false);
        }

        var latestFit = fit.forApplication(ownerId, applicationId).map(f -> f.fit())
                .map(f -> new BriefWriter.Fit(f.score(), f.matchedSkills(), f.gaps(), f.dealbreakers(), f.rationale()))
                .orElse(null);
        var details = application.getPostingDetails();
        var input = new BriefInput(kind, interviewId, application.getCompanyName(), application.getRole(),
                details.location(), details.remote(),
                application.getEmploymentType() == null ? null : label(application.getEmploymentType().name()),
                pay(application), application.getJobDescription(), application.getNotes(), resumeText, latestFit,
                peopleOn(applicationId), rounds.stream().map(BriefService::round).toList(), storyBank(ownerId),
                LocalDate.now(clock));
        return Optional.of(new Prepared(input, provider, apiKey));
    }

    /**
     * Reading order: each kind once, except round updates, one per round that has happened
     * or has debrief notes (cancelled ones only when an update was already written).
     */
    private List<BriefSectionResponse> layout(UUID applicationId, List<BriefSection> stored) {
        var byKey = new HashMap<String, BriefSection>();
        stored.forEach(s -> byKey.put(key(s.getKind(), s.getInterviewId()), s));
        var now = Instant.now(clock);

        var result = new ArrayList<BriefSectionResponse>();
        for (var kind : SectionKind.values()) {
            if (!kind.isPerRound()) {
                var section = byKey.get(key(kind, null));
                result.add(section != null ? toResponse(section, kind, null) : empty(kind, null));
                continue;
            }
            for (var round : interviews.findAllByApplicationIdOrderByStartsAtAsc(applicationId)) {
                var section = byKey.get(key(kind, round.getId()));
                var happened = round.getStartsAt().isBefore(now) || hasText(round.getDebriefNotes());
                var cancelled = round.getOutcome() == InterviewOutcome.CANCELLED;
                if (section != null) result.add(toResponse(section, kind, round.getId()));
                else if (happened && !cancelled) result.add(empty(kind, round.getId()));
            }
        }
        return result;
    }

    private static String key(SectionKind kind, UUID interviewId) {
        return kind + "/" + interviewId;
    }

    private static BriefSectionResponse empty(SectionKind kind, UUID interviewId) {
        var content = kind == SectionKind.CHECKLIST ? CHECKLIST_TEMPLATE : null;
        return new BriefSectionResponse(kind, interviewId, kind.title(), kind.isWritten(), content, null, null, false, null);
    }

    private static BriefSectionResponse toResponse(BriefSection s, SectionKind kind, UUID interviewId) {
        return new BriefSectionResponse(kind, interviewId, kind.title(), kind.isWritten(), s.getContent(),
                s.getGeneratedAt(), s.getModel(), s.isEdited(), s.getVersion());
    }

    /** The chosen resume, else the one the fit was checked against, else the library's default. */
    private Optional<Document> resume(UUID ownerId, UUID applicationId, Optional<InterviewBrief> brief) {
        var candidates = new ArrayList<UUID>();
        brief.map(InterviewBrief::getResumeDocumentId).ifPresent(candidates::add);
        fit.forApplication(ownerId, applicationId).map(f -> f.fit()).filter(Objects::nonNull)
                .map(f -> f.resumeDocumentId()).ifPresent(candidates::add);
        resumes.list(ownerId).stream().filter(ResumeResponse::isDefault).map(ResumeResponse::documentId)
                .findFirst().ifPresent(candidates::add);
        return candidates.stream()
                .map(id -> documents.find(ownerId, applicationId, id).or(() -> documents.find(ownerId, null, id)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private boolean isRoundOf(UUID ownerId, UUID applicationId, UUID interviewId) {
        return interviews.findByIdAndOwnerId(interviewId, ownerId)
                .filter(i -> i.getApplication().getId().equals(applicationId))
                .isPresent();
    }

    /** Created on first write, edit or resume choice; two first writes at once share one brief. */
    private InterviewBrief findOrCreate(UUID ownerId, UUID applicationId) {
        return briefs.findByApplicationIdAndOwnerId(applicationId, ownerId).orElseGet(() -> {
            briefs.insertIfMissing(UUID.randomUUID(), ownerId, applicationId, Instant.now(clock));
            return briefs.findByApplicationIdAndOwnerId(applicationId, ownerId).orElseThrow();
        });
    }

    private List<BriefWriter.StoryLine> storyBank(UUID ownerId) {
        return stories.all(ownerId).stream()
                .map(s -> new BriefWriter.StoryLine(s.getTitle(), s.getTags(), s.getSituation(), s.getTask(),
                        s.getAction(), s.getResult(), s.getMetrics()))
                .toList();
    }

    private List<BriefWriter.PersonLine> peopleOn(UUID applicationId) {
        return people.findAllByApplicationIdOrderByAddedAtAsc(applicationId).stream()
                .map(link -> {
                    var person = link.getPerson();
                    return new BriefWriter.PersonLine(person.getName(), label(link.getRole().name()), person.getTitle(),
                            person.getCompany() == null ? null : person.getCompany().getName());
                })
                .toList();
    }

    private static BriefWriter.Round round(Interview i) {
        var when = ROUND_TIME.format(i.getLocalStart()) + " (" + i.getTimeZone() + ")";
        return new BriefWriter.Round(i.getId(), label(i.getType().name()), when,
                i.getInterviewers().stream().map(p -> p.getTitle() == null ? p.getName() : p.getName() + ", " + p.getTitle()).toList(),
                i.getPrepNotes(), i.getDebriefNotes(), i.getOutcome() == null ? null : label(i.getOutcome().name()));
    }

    /** "120,000-150,000 USD a year", or the posting's own words when it gave no numbers. */
    private static String pay(JobApplication application) {
        var d = application.getPostingDetails();
        if (d.salaryMin() == null && d.salaryMax() == null) return d.salaryText();
        var range = d.salaryMin() != null && d.salaryMax() != null && !d.salaryMin().equals(d.salaryMax())
                ? "%,d-%,d".formatted(d.salaryMin(), d.salaryMax())
                : "%,d".formatted(d.salaryMin() != null ? d.salaryMin() : d.salaryMax());
        var currency = d.salaryCurrency() == null ? "" : " " + d.salaryCurrency();
        var period = d.salaryPeriod() == null ? "" : " a " + d.salaryPeriod();
        return range + currency + period;
    }

    /** "PHONE_SCREEN" reads as "Phone screen". */
    static String label(String constant) {
        var words = constant.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private <T> T read(Supplier<T> work) {
        var readOnly = new TransactionTemplate(tx.getTransactionManager());
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> work.get());
    }

    private Optional<String> chooseProvider(Set<String> saved) {
        return PREFERENCE.stream()
                .filter(saved::contains)
                .filter(id -> writers.stream().anyMatch(w -> w.provider().equals(id)))
                .findFirst();
    }

    private BriefWriter writer(String provider) {
        return writers.stream().filter(w -> w.provider().equals(provider)).findFirst().orElseThrow();
    }

    private CompanyResearcher researcher(String provider) {
        return researchers.stream().filter(r -> r.provider().equals(provider)).findFirst()
                .orElseThrow(() -> new BriefException("Company research isn't available with this AI provider.", false));
    }
}
