package dev.beacon.application.digest;

import dev.beacon.contracts.home.HomeTodoResponse;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What the weekly email says: Home's sections, worked out on the server for one user at one moment.
 *
 * @param name       the user's first name; null when they gave none
 * @param newMatches every new match, though only the best few are listed
 */
public record Digest(String name, LocalDate date, List<Flagged> attention, List<Interview> upcoming,
                     List<Match> queue, int newMatches, List<Deck> decks, Week week,
                     List<HomeTodoResponse> missingSentResume, List<HomeTodoResponse> openSuggestions) {

    public record Flagged(UUID applicationId, String employer, String role, String reason) {}

    public record Interview(UUID applicationId, String employer, String role, ZonedDateTime startsAt, long cardsDue) {}

    public record Match(String title, String company, Integer score, String url) {}

    public record Deck(UUID id, String name, long due) {}

    /** @param responded of everything applied to, how many heard back */
    public record Week(long sentThisWeek, long sentLastWeek, long applied, long responded, List<Stage> stages) {}

    public record Stage(String label, long count) {}

    public long cardsDue() {
        return decks.stream().mapToLong(Deck::due).sum();
    }

    /** Nothing is waiting on the user; the numbers still go out. */
    public boolean caughtUp() {
        return attention.isEmpty() && upcoming.isEmpty() && queue.isEmpty() && decks.isEmpty()
                && missingSentResume.isEmpty() && openSuggestions.isEmpty();
    }
}
