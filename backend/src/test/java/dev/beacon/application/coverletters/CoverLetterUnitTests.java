package dev.beacon.application.coverletters;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CoverLetterUnitTests {

    @Test
    void letterHtmlTurnsBlankLinesIntoParagraphsAndEscapesText() {
        var html = CoverLetterService.letterHtml("Dear Hiring Team,\n\nLine one\nline two & <more>\n\n\nJordan", LocalDate.parse("2026-10-24"));

        assertThat(html).contains("<p class=\"date\">October 24, 2026</p>");
        assertThat(html).contains("<p>Dear Hiring Team,</p>");
        assertThat(html).contains("<p>Line one<br>line two &amp; &lt;more&gt;</p>");
        assertThat(html).contains("<p>Jordan</p>");
    }
}
