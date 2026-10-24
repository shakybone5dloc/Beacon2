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
    void snippetCutsLongTextAtAWordBoundary() {
        var snippet = Feeds.snippet("word ".repeat(100));

        assertThat(snippet).endsWith("word…");
        assertThat(snippet.length()).isLessThanOrEqualTo(241);
    }
}
