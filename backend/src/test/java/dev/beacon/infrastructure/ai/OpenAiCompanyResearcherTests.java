package dev.beacon.infrastructure.ai;

import dev.beacon.application.briefs.BriefException;
import dev.beacon.application.briefs.BriefWriter.BriefInput;
import dev.beacon.application.usage.AiUsage;
import dev.beacon.domain.briefs.SectionKind;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompanyResearcherTests {

    private static final String URL = "https://api.openai.com/v1/responses";

    private static final BriefInput INPUT = new BriefInput(SectionKind.COMPANY, null, "GHX", "Senior Java Engineer",
            null, true, null, null, "Build supply chain services.", null, "Jordan Smith", null, List.of(), List.of(),
            List.of(), LocalDate.of(2026, 11, 5));

    private record Setup(OpenAiCompanyResearcher researcher, MockRestServiceServer server) {}

    private static Setup setup() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        return new Setup(new OpenAiCompanyResearcher(builder, "gpt-6-sol"), server);
    }

    @Test
    void searchesWithTheResponsesApiAndCitesWhatItFound() {
        var s = setup();
        // "Héalth" makes the code point and UTF-16 positions differ after it.
        s.server().expect(requestTo(URL))
                .andExpect(jsonPath("$.model").value("gpt-6-sol"))
                .andExpect(jsonPath("$.tools[0].type").value("web_search"))
                .andExpect(jsonPath("$.instructions").value(BriefPrompt.INSTRUCTIONS))
                .andExpect(jsonPath("$.input").value(BriefPrompt.userMessage(INPUT)))
                .andRespond(withSuccess("""
                        {"output": [
                          {"type": "web_search_call", "status": "completed", "action": {"type": "search"}},
                          {"type": "web_search_call", "status": "completed", "action": {"type": "open_page"}},
                          {"type": "message", "content": [{"type": "output_text",
                            "text": "### Snapshot\\n\\nGHX serves Héalth systems. 😀 It is in Colorado.",
                            "annotations": [
                              {"type": "url_citation", "start_index": 14, "end_index": 40,
                               "url": "https://www.ghx.com/about", "title": "About GHX"},
                              {"type": "url_citation", "start_index": 43, "end_index": 61,
                               "url": "https://co.example/ghx", "title": "GHX in Colorado"}]}]}],
                         "usage": {"input_tokens": 24000, "input_tokens_details": {"cached_tokens": 1000},
                                   "output_tokens": 900}}
                        """, MediaType.APPLICATION_JSON));
        var reported = new ArrayList<AiUsage>();

        var markdown = s.researcher().research(INPUT, "sk-test", reported::add);

        assertThat(markdown).isEqualTo("""
                ### Snapshot

                GHX serves Héalth systems. [[1]](https://www.ghx.com/about) 😀 It is in Colorado. \
                [[2]](https://co.example/ghx)

                ### Sources

                1. [About GHX](https://www.ghx.com/about)
                2. [GHX in Colorado](https://co.example/ghx)""");
        assertThat(reported).containsExactly(new AiUsage("openai", "gpt-6-sol", 24000, 1000, 900, 2));
        s.server().verify();
    }

    @Test
    void aRefusalOrAnAnswerWithNoSourcesIsAnError() {
        var s = setup();
        s.server().expect(requestTo(URL)).andRespond(withSuccess("""
                {"output": [{"type": "message", "content": [{"type": "refusal", "refusal": "No."}]}], "usage": {}}
                """, MediaType.APPLICATION_JSON));
        s.server().expect(requestTo(URL)).andRespond(withSuccess("""
                {"output": [{"type": "message", "content": [{"type": "output_text", "text": "GHX is great.",
                  "annotations": []}]}], "usage": {}}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> s.researcher().research(INPUT, "sk-test", call -> {}))
                .isInstanceOf(BriefException.class).hasMessageContaining("declined");
        assertThatThrownBy(() -> s.researcher().research(INPUT, "sk-test", call -> {}))
                .isInstanceOf(BriefException.class).hasMessageContaining("nothing it could cite");
    }
}
