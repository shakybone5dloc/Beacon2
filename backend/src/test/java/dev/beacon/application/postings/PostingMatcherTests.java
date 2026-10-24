package dev.beacon.application.postings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostingMatcherTests {

    private static JobPosting posting(String title, String location, boolean remote, String... tags) {
        return new JobPosting("test", "1", title, "Acme", location, remote, "https://example.com/1",
                null, null, null, null, null, null, null, List.of(tags), null);
    }

    @Test
    void everyKeywordMustAppear() {
        var p = posting("Senior Java Engineer", null, true, "spring");

        assertThat(PostingMatcher.matchesKeywords(p, "java spring")).isTrue();
        assertThat(PostingMatcher.matchesKeywords(p, "java kotlin")).isFalse();
    }

    @Test
    void keywordsMatchWholeWordsOnly() {
        assertThat(PostingMatcher.matchesKeywords(posting("JavaScript Developer", null, true), "java")).isFalse();
        assertThat(PostingMatcher.matchesKeywords(posting("Java/Kotlin Developer", null, true), "java")).isTrue();
    }

    @Test
    void keywordsWithSymbolsStillMatch() {
        assertThat(PostingMatcher.matchesKeywords(posting("C++ Engineer", null, true), "c++")).isTrue();
        assertThat(PostingMatcher.matchesKeywords(posting("Senior .NET Developer", null, true), ".net")).isTrue();
    }

    @Test
    void locationAliasesMatchEachOther() {
        assertThat(PostingMatcher.matchesLocation("United States", "USA")).isTrue();
        assertThat(PostingMatcher.matchesLocation("USA only", "united states")).isTrue();
        assertThat(PostingMatcher.matchesLocation("Germany", "USA")).isFalse();
    }

    @Test
    void shortAliasesDoNotMatchInsideOtherWords() {
        assertThat(PostingMatcher.matchesLocation("Russia", "us")).isFalse();
    }

    @Test
    void worldwideAndUnknownLocationsMatchEverything() {
        assertThat(PostingMatcher.matchesLocation("Worldwide", "USA")).isTrue();
        assertThat(PostingMatcher.matchesLocation("Anywhere in the world", "Denver")).isTrue();
        assertThat(PostingMatcher.matchesLocation(null, "Denver")).isTrue();
    }

    @Test
    void remoteOnlyExcludesOnSiteRoles() {
        var query = new PostingQuery("java", "", true);

        assertThat(PostingMatcher.matches(posting("Java Engineer", "Denver", false), query)).isFalse();
        assertThat(PostingMatcher.matches(posting("Java Engineer", "Denver", true), query)).isTrue();
    }
}
