package dev.beacon.application.interviews;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IcsCalendarTests {

    @Test
    void escapesTextValues() {
        assertThat(IcsCalendar.text("a\\b; c, d\r\ne\nf")).isEqualTo("a\\\\b\\; c\\, d\\ne\\nf");
    }

    @Test
    void leavesShortLinesAlone() {
        assertThat(IcsCalendar.fold("SUMMARY:Phone screen")).isEqualTo("SUMMARY:Phone screen");
    }

    @Test
    void foldsLongLinesAt75BytesWithoutSplittingACharacter() {
        var line = "DESCRIPTION:" + "é".repeat(100);

        var folded = IcsCalendar.fold(line);

        var pieces = folded.split("\r\n");
        for (var piece : pieces) {
            assertThat(piece.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75);
        }
        for (var i = 1; i < pieces.length; i++) {
            assertThat(pieces[i]).startsWith(" ");
        }
        // Unfolding (dropping each CRLF and the space after it) gives the line back.
        assertThat(folded.replace("\r\n ", "")).isEqualTo(line);
    }
}
