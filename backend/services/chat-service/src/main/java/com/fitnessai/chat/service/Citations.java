package com.fitnessai.chat.service;

import com.fitnessai.chat.dto.ChatDtos.Citation;
import com.fitnessai.chat.dto.ChatDtos.RetrievedChunk;
import com.fitnessai.chat.dto.ChatDtos.RetrievedContext;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbered sources for one reply. Retrieved documents are [S1], [S2]... (one
 * number per document, however many excerpts matched); web results found by
 * the searchWeb tool are [W1], [W2]... The model cites them inline and only
 * the sources it actually cited are returned to the user.
 */
public final class Citations {

    /** One bracket holding one or more ids: [S1], [W2], [S1, W3]. */
    static final Pattern MARKER = Pattern.compile("\\[((?:[SW]\\d+)(?:\\s*,\\s*[SW]\\d+)*)]");
    private static final Pattern ID = Pattern.compile("[SW]\\d+");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private Citations() {
    }

    /** Document sources in excerpt order, keyed by filename so repeated excerpts share a number. */
    static Map<String, Citation> documents(RetrievedContext context) {
        Map<String, Citation> byFile = new LinkedHashMap<>();
        if (context == null || context.chunks() == null) return byFile;
        for (RetrievedChunk c : context.chunks()) {
            String name = c.filename() == null || c.filename().isBlank() ? "document" : c.filename();
            byFile.computeIfAbsent(name, n -> new Citation("S" + (byFile.size() + 1), "document", n, null));
        }
        return byFile;
    }

    /** The available sources the reply cites, in the order they are first cited. */
    static List<Citation> cited(String reply, List<Citation> available) {
        if (reply == null || available.isEmpty()) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = MARKER.matcher(reply);
        while (m.find()) {
            Matcher id = ID.matcher(m.group(1));
            while (id.find()) ids.add(id.group());
        }
        List<Citation> out = new ArrayList<>();
        for (String id : ids) {
            available.stream().filter(c -> c.id().equals(id)).findFirst().ifPresent(out::add);
        }
        return out;
    }

    /** Drops markers that point at no real source, so a hallucinated [S9] never reaches the user. */
    static String stripUnknown(String reply, List<Citation> available) {
        if (reply == null) return null;
        Set<String> known = new LinkedHashSet<>();
        available.forEach(c -> known.add(c.id()));
        Matcher m = MARKER.matcher(reply);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            List<String> kept = ID.matcher(m.group(1)).results().map(r -> r.group()).filter(known::contains).toList();
            m.appendReplacement(sb, kept.isEmpty() ? "" : Matcher.quoteReplacement("[" + String.join(", ", kept) + "]"));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll(" +([.,;:!?])", "$1");
    }

    static String toJson(List<Citation> citations) {
        return citations == null || citations.isEmpty() ? null : JSON.writeValueAsString(citations);
    }

    public static List<Citation> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<Citation>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }
}
