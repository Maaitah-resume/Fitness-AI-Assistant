package com.fitnessai.chat.service;

import com.fitnessai.chat.dto.ChatDtos.Citation;
import com.fitnessai.chat.dto.ChatDtos.RetrievedChunk;
import com.fitnessai.chat.dto.ChatDtos.RetrievedContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CitationsTest {

    private final RetrievedContext context = new RetrievedContext("", List.of(
            new RetrievedChunk("Rest 2-3 min.", "Strength.pdf", "d1", 0.9),
            new RetrievedChunk("Protein 1.6 g/kg.", "Nutrition.pdf", "d2", 0.8),
            new RetrievedChunk("Squat deep.", "Strength.pdf", "d1", 0.7)), List.of("Squat trains quads"), List.of());

    @Test
    void documentsGetOneNumberEachAndThePromptLabelsExcerptsWithIt() {
        Map<String, Citation> docs = Citations.documents(context);
        assertThat(docs.values()).extracting(Citation::id).containsExactly("S1", "S2");
        String text = SystemPrompt.contextText(context);
        assertThat(text).contains("[S1] Strength.pdf: Rest 2-3 min.", "[S2] Nutrition.pdf: Protein", "[S1] Strength.pdf: Squat deep.",
                "- Squat trains quads");
        assertThat(SystemPrompt.build(null, null, null, context, false)).contains("=== SOURCES AND CITATIONS ===");
    }

    @Test
    void onlyCitedSourcesAreReturnedInCitationOrder() {
        List<Citation> available = new ArrayList<>(Citations.documents(context).values());
        available.add(new Citation("W1", "web", "who.int", "https://example.org/who"));
        String reply = "Eat enough protein [S2]. Recent guidance agrees [W1, S2]. Rest well.";
        assertThat(Citations.cited(reply, available)).extracting(Citation::id).containsExactly("S2", "W1");
        assertThat(Citations.cited("No markers here.", available)).isEmpty();
    }

    @Test
    void markersForUnknownSourcesAreRemoved() {
        List<Citation> available = new ArrayList<>(Citations.documents(context).values());
        assertThat(Citations.stripUnknown("A fact [S9]. Another [S1, W4]. Done [W2].", available))
                .isEqualTo("A fact. Another [S1]. Done.");
    }

    @Test
    void citationsRoundTripThroughJson() {
        List<Citation> list = List.of(new Citation("W1", "web", "Title", "https://x.test"));
        assertThat(Citations.fromJson(Citations.toJson(list))).isEqualTo(list);
        assertThat(Citations.toJson(List.of())).isNull();
        assertThat(Citations.fromJson("not json")).isEmpty();
    }

    @Test
    void webSearchParsesGroundingChunks() {
        Map<String, Object> response = Map.of("candidates", List.of(Map.of(
                "content", Map.of("parts", List.of(Map.of("text", "Creatine is "), Map.of("text", "well studied."))),
                "groundingMetadata", Map.of("groundingChunks", List.of(
                        Map.of("web", Map.of("uri", "https://a.test", "title", "nih.gov")),
                        Map.of("web", Map.of("uri", "https://a.test", "title", "nih.gov")),
                        Map.of("web", Map.of("uri", "https://b.test")))))));
        WebSearch.Result r = WebSearch.parse(response);
        assertThat(r.findings()).isEqualTo("Creatine is well studied.");
        assertThat(r.sources()).containsExactly(new WebSearch.Source("nih.gov", "https://a.test"),
                new WebSearch.Source("https://b.test", "https://b.test"));
        assertThat(WebSearch.parse(Map.of()).sources()).isEmpty();
    }

    @Test
    void duckDuckGoFallbackUnwrapsLinksAndReadsSnippets() {
        String html = """
                <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fhealth.example.org%2Fcreatine&amp;rut=abc">Does <b>Creatine</b> Cause Hair Loss?</a>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">You&#x27;ve heard <b>creatine</b> can cause hair loss.</a>
                <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fpubmed.example.gov%2F123">Creatine and DHT</a>
                <a class="result__snippet" href="//duckduckgo.com/l/?uddg=y">A 2009 study in rugby players.</a>
                """;
        WebSearch.Result r = WebSearch.parseDuckDuckGo(html);
        assertThat(r.sources()).containsExactly(
                new WebSearch.Source("Does Creatine Cause Hair Loss?", "https://health.example.org/creatine"),
                new WebSearch.Source("Creatine and DHT", "https://pubmed.example.gov/123"));
        assertThat(r.findings()).contains("You've heard creatine can cause hair loss.", "A 2009 study in rugby players.");
    }
}
