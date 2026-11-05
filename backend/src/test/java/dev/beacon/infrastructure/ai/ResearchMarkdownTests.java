package dev.beacon.infrastructure.ai;

import dev.beacon.application.briefs.BriefException;
import dev.beacon.infrastructure.ai.ResearchMarkdown.Cited;
import dev.beacon.infrastructure.ai.ResearchMarkdown.Range;
import dev.beacon.infrastructure.ai.ResearchMarkdown.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchMarkdownTests {

    private static final Source ABOUT = new Source("About GHX", "https://www.ghx.com/about");
    private static final Source NEWS = new Source("GHX names new CEO", "https://news.example/ghx-ceo");

    @Test
    void numbersSourcesInTheOrderTheyreFirstCitedAndListsThemAtTheEnd() {
        var markdown = ResearchMarkdown.render(List.of(
                new Cited("### Snapshot\n\n", List.of()),
                new Cited("GHX runs a supply chain network for hospitals.", List.of(ABOUT)),
                new Cited(" It has about 1,000 employees.\n\n", List.of(ABOUT, ABOUT)),
                new Cited("### Leadership\n\nThe CEO is Tracy Pound.", List.of(NEWS, ABOUT))));

        assertThat(markdown).isEqualTo("""
                ### Snapshot

                GHX runs a supply chain network for hospitals. [[1]](https://www.ghx.com/about) \
                It has about 1,000 employees. [[1]](https://www.ghx.com/about)

                ### Leadership

                The CEO is Tracy Pound. [[2]](https://news.example/ghx-ceo) [[1]](https://www.ghx.com/about)

                ### Sources

                1. [About GHX](https://www.ghx.com/about)
                2. [GHX names new CEO](https://news.example/ghx-ceo)""");
    }

    @Test
    void aSourceWithoutATitleIsListedByItsAddressAndBracketsCantBreakTheLink() {
        var markdown = ResearchMarkdown.render(List.of(
                new Cited("One.", List.of(new Source(null, "https://a.example"))),
                new Cited(" Two.", List.of(new Source("[PDF] Annual report", "https://b.example")))));

        assertThat(markdown).endsWith("""
                1. [https://a.example](https://a.example)
                2. [(PDF) Annual report](https://b.example)""");
    }

    @Test
    void researchThatCitesNothingIsRefused() {
        assertThatThrownBy(() -> ResearchMarkdown.render(List.of(new Cited("GHX is great.", List.of()))))
                .isInstanceOf(BriefException.class)
                .hasMessageContaining("nothing it could cite");
    }

    @Test
    void splitsTextAtTheEndOfEachCitedRange() {
        var text = "GHX serves hospitals. It is in Colorado. Unsourced.";

        var parts = ResearchMarkdown.fromRanges(text, List.of(
                new Range(40, NEWS), new Range(21, ABOUT), new Range(40, ABOUT), new Range(999, NEWS)));

        assertThat(parts).containsExactly(
                new Cited("GHX serves hospitals.", List.of(ABOUT)),
                new Cited(" It is in Colorado.", List.of(NEWS, ABOUT)),
                new Cited(" Unsourced.", List.of(NEWS)),
                new Cited("", List.of()));
    }
}
