package dev.beacon.infrastructure.postings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedsTests {

    @Test
    void plainDecodesNamedAndNumericEntities() {
        assertThat(Feeds.plain("Java Developer&#x2f;AWS &amp; Cloud &#8211; Remote")).isEqualTo("Java Developer/AWS & Cloud – Remote");
    }

    @Test
    void plainCollapsesAllWhitespaceAndStripsTags() {
        assertThat(Feeds.plain("<p>Line one</p>\n\n\t<ul><li>two</li></ul>")).isEqualTo("Line one two");
    }

    @Test
    void descriptionKeepsParagraphsAndListItemsOnTheirOwnLines() {
        var html = "<h2>About</h2><p>We build   tools.</p><ul><li>Java</li><li>Spring &amp; Postgres</li></ul><p>Apply<br>today</p>";

        assertThat(Feeds.description(html)).isEqualTo("""
                About

                We build tools.

                • Java
                • Spring & Postgres

                Apply
                today""");
    }

    @Test
    void descriptionIsNullForEmptyHtml() {
        assertThat(Feeds.description("<p> </p>")).isNull();
        assertThat(Feeds.description(null)).isNull();
    }

    @Test
    void snippetCutsLongTextAtAWordBoundary() {
        var snippet = Feeds.snippet("word ".repeat(100));

        assertThat(snippet).endsWith("word…");
        assertThat(snippet.length()).isLessThanOrEqualTo(241);
    }
}
