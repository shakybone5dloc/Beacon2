package dev.beacon.infrastructure.documents;

import dev.beacon.DocumentTestDoubles;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTextTests {

    @Test
    void reflowJoinsWrappedLinesAndKeepsParagraphsAndBullets() {
        var wrapped = """
                GitLab is the intelligent orchestration platform for DevSecOps. GitLab enables
                organizations to increase developer productivity and reduce security risk.
                What you'll do:
                • Own the full sales cycle for enterprise accounts
                • Build relationships with technical buyers
                We value results. This line is long enough to be wrapped by the printed page
                width and continues here.""";

        assertThat(DocumentText.reflow(wrapped)).isEqualTo("""
                GitLab is the intelligent orchestration platform for DevSecOps. GitLab enables \
                organizations to increase developer productivity and reduce security risk.

                What you'll do:
                • Own the full sales cycle for enterprise accounts
                • Build relationships with technical buyers
                We value results. This line is long enough to be wrapped by the printed page \
                width and continues here.""");
    }

    @Test
    void reflowLeavesShortTextAlone() {
        assertThat(DocumentText.reflow("Java\nSpring\nPostgres")).isEqualTo("Java\nSpring\nPostgres");
    }

    @Test
    void extractsTextFromPdfsAndPlainText() {
        var pdf = DocumentTestDoubles.pdfWithText("Jordan Smith", "Java developer");

        assertThat(DocumentText.extract(pdf, "application/pdf")).hasValueSatisfying(text ->
                assertThat(text).contains("Jordan Smith").contains("Java developer"));
        assertThat(DocumentText.extract("# Resume".getBytes(StandardCharsets.UTF_8), "text/markdown")).hasValue("# Resume");
        assertThat(DocumentText.extract(new byte[]{1, 2}, "application/msword")).isEmpty();
    }
}
