package dev.beacon.application.searches;

import dev.beacon.contracts.postings.JobPostingResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFiltersTests {

    private static JobPostingResponse pay(Integer min, Integer max, String period) {
        return new JobPostingResponse("x", "1", "Java Engineer", "Acme", null, true, "https://x.example", null, null,
                min, max, "USD", period, null, List.of(), null, null);
    }

    @Test
    void excludedWordsMatchWholeWordsAndPhrases() {
        assertThat(SearchFilters.excluded("Senior Java Engineer", List.of("senior"))).isTrue();
        assertThat(SearchFilters.excluded("Seniority Analyst", List.of("senior"))).isFalse();
        assertThat(SearchFilters.excluded("Desenvolvedor(a) Java / Kafka Sênior", List.of("Senior"))).isTrue();
        assertThat(SearchFilters.excluded("Sr. Java Engineer (Contract)", List.of("contract"))).isTrue();
        assertThat(SearchFilters.excluded("Engineering Manager, Java", List.of("engineering manager"))).isTrue();
        assertThat(SearchFilters.excluded("Java Engineer", List.of(" ", ""))).isFalse();
    }

    @Test
    void thePayFloorOnlyJudgesYearlyPayThatsStated() {
        assertThat(SearchFilters.paysEnough(pay(90_000, 120_000, "year"), 100_000)).isTrue();
        assertThat(SearchFilters.paysEnough(pay(60_000, 80_000, "year"), 100_000)).isFalse();
        assertThat(SearchFilters.paysEnough(pay(null, null, null), 100_000)).isTrue();
        assertThat(SearchFilters.paysEnough(pay(60, 70, "hour"), 100_000)).isTrue();
        assertThat(SearchFilters.paysEnough(pay(60_000, 80_000, "year"), null)).isTrue();
    }

    @Test
    void anUndatedPostingCountsAsRecent() {
        var now = Instant.parse("2026-11-02T12:00:00Z");

        assertThat(SearchFilters.recentEnough(null, 7, now)).isTrue();
        assertThat(SearchFilters.recentEnough(Instant.parse("2026-10-27T12:00:00Z"), 7, now)).isTrue();
        assertThat(SearchFilters.recentEnough(Instant.parse("2026-10-25T12:00:00Z"), 7, now)).isFalse();
    }

    @Test
    void theSameJobWrittenDifferentlyHasOneKey() {
        assertThat(MatchKeys.of("Acme, Inc.", "Sr. Java Engineer (Remote)"))
                .isEqualTo(MatchKeys.of("ACME", "sr java engineer remote"));
        assertThat(MatchKeys.of("Acme", "Java Engineer")).isNotEqualTo(MatchKeys.of("Globex", "Java Engineer"));
        assertThat(MatchKeys.of(null, "Java Engineer")).hasSize(64);
    }
}
