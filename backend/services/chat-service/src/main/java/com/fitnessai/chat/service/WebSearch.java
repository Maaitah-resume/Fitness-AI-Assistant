package com.fitnessai.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web search through Gemini's built-in Google Search grounding. Called over
 * REST rather than through Spring AI because we need the grounding metadata
 * (the pages the answer came from), which the ChatClient doesn't expose. It
 * runs as a separate request so it never clashes with the chat's own tools.
 *
 * Grounding needs a paid Gemini plan; free keys get a 429. Then (and for the
 * next half hour, to skip the wasted call) we fall back to DuckDuckGo's HTML
 * results, which need no key: the model reads the snippets itself.
 */
@Component
public class WebSearch {

    /** sources: the pages Google returned, in the order Gemini cited them. */
    public record Result(String findings, List<Source> sources) {
    }

    public record Source(String title, String url) {
    }

    private static final int MAX_SOURCES = 5;
    private static final Logger log = LoggerFactory.getLogger(WebSearch.class);
    private static final Duration GROUNDING_RETRY_AFTER = Duration.ofMinutes(30);
    private static final Pattern DDG_TITLE = Pattern.compile("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
    private static final Pattern DDG_SNIPPET = Pattern.compile("class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);

    private final RestClient http;
    private final RestClient fallback;
    private volatile Instant groundingBlockedUntil = Instant.MIN;
    private final String apiKey;
    private final String model;
    private final boolean enabled;

    public WebSearch(@Value("${spring.ai.google.genai.api-key:not-configured}") String apiKey,
                     @Value("${web-search.model:${spring.ai.google.genai.chat.options.model:gemini-3.5-flash}}") String model,
                     @Value("${web-search.enabled:true}") boolean enabled,
                     @Value("${web-search.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(40));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.fallback = RestClient.builder().requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) GymAI-coach").build();
        this.apiKey = apiKey;
        this.model = model;
        this.enabled = enabled;
    }

    public boolean available() {
        return enabled;
    }

    public Result search(String query) {
        boolean hasKey = apiKey != null && !apiKey.isBlank() && !"not-configured".equals(apiKey);
        if (hasKey && Instant.now().isAfter(groundingBlockedUntil)) {
            try {
                Result r = grounded(query);
                if (!r.sources().isEmpty()) return r;
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() != 429 && e.getStatusCode().value() != 403) throw e;
                log.info("Gemini search grounding unavailable ({}); using DuckDuckGo for {}", e.getStatusCode().value(),
                        GROUNDING_RETRY_AFTER);
                groundingBlockedUntil = Instant.now().plus(GROUNDING_RETRY_AFTER);
            }
        }
        return duckDuckGo(query);
    }

    Result duckDuckGo(String query) {
        String html = fallback.get()
                .uri("https://html.duckduckgo.com/html/?q={q}", query)
                .retrieve()
                .body(String.class);
        return parseDuckDuckGo(html);
    }

    /** Titles, real URLs (unwrapped from DuckDuckGo's redirect) and snippets of the top results. */
    static Result parseDuckDuckGo(String html) {
        if (html == null) return new Result("", List.of());
        List<Source> sources = new ArrayList<>();
        StringBuilder findings = new StringBuilder();
        Matcher t = DDG_TITLE.matcher(html);
        Matcher sn = DDG_SNIPPET.matcher(html);
        int searchFrom = 0;
        while (t.find() && sources.size() < MAX_SOURCES) {
            String url = unwrap(HtmlUtils.htmlUnescape(t.group(1)));
            if (url == null || url.contains("duckduckgo.com/y.js")) continue; // ads
            String title = text(t.group(2));
            String snippet = sn.find(Math.max(t.end(), searchFrom)) ? text(sn.group(1)) : "";
            if (!snippet.isEmpty()) searchFrom = sn.end();
            if (sources.stream().anyMatch(x -> x.url().equals(url))) continue;
            sources.add(new Source(title.isEmpty() ? url : title, url));
            findings.append("- ").append(title).append(": ").append(snippet).append('\n');
        }
        return new Result(findings.toString().strip(), sources);
    }

    private static String unwrap(String href) {
        int i = href.indexOf("uddg=");
        if (i < 0) return href.startsWith("http") ? href : null;
        String enc = href.substring(i + 5);
        int amp = enc.indexOf('&');
        return URLDecoder.decode(amp < 0 ? enc : enc.substring(0, amp), StandardCharsets.UTF_8);
    }

    private static String text(String html) {
        return HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", "")).replaceAll("\\s+", " ").strip();
    }

    private Result grounded(String query) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", """
                        Search the web and summarise, factually and concisely, what reliable sources say about:
                        %s
                        Prefer health authorities, peer-reviewed research and established sports-science sources."""
                        .formatted(query))))),
                "tools", List.of(Map.of("google_search", Map.of())));
        Map<?, ?> response = http.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        return parse(response);
    }

    @SuppressWarnings("unchecked")
    static Result parse(Map<?, ?> response) {
        List<Map<String, Object>> candidates = response == null ? null : (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) return new Result("", List.of());
        Map<String, Object> first = candidates.get(0);

        StringBuilder text = new StringBuilder();
        Map<String, Object> content = (Map<String, Object>) first.get("content");
        if (content != null && content.get("parts") instanceof List<?> parts) {
            for (Object p : parts) {
                if (p instanceof Map<?, ?> part && part.get("text") instanceof String s) text.append(s);
            }
        }

        // groundingChunks hold the pages; de-duplicate by URL and keep the first few.
        Map<String, Source> byUrl = new LinkedHashMap<>();
        Map<String, Object> grounding = (Map<String, Object>) first.get("groundingMetadata");
        if (grounding != null && grounding.get("groundingChunks") instanceof List<?> chunks) {
            for (Object c : chunks) {
                if (!(c instanceof Map<?, ?> chunk) || !(chunk.get("web") instanceof Map<?, ?> web)) continue;
                if (!(web.get("uri") instanceof String uri) || uri.isBlank()) continue;
                String title = web.get("title") instanceof String t && !t.isBlank() ? t : uri;
                byUrl.putIfAbsent(uri, new Source(title, uri));
                if (byUrl.size() == MAX_SOURCES) break;
            }
        }
        return new Result(text.toString().strip(), new ArrayList<>(byUrl.values()));
    }
}
